import { useEffect, useState } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { PlusIcon, XIcon } from "lucide-react";
import {
  DayColumnHeadings,
  DescriptionField,
  TopicRow,
  VisibilityToggle,
} from "@/components/TrackFormParts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { checkTrackName, createTrack } from "@/lib/tracks";

const NAME_CHECK_DELAY_MS = 300;

let nextTopicId = 0;
const emptyTopic = () => ({ id: nextTopicId++, name: "", files: [] });

const emptyForm = () => ({
  name: "",
  description: "",
  visibility: "PRIVATE",
  topics: [emptyTopic()],
});

/**
 * Asks the server whether the track name is free, once the user pauses typing.
 * Returns "idle", "checking", "available", "taken" or "unknown" (check failed),
 * plus markTaken for when the server rejects the name on submit anyway.
 */
const useNameAvailability = (name) => {
  const trimmed = name.trim();
  // The answer remembers which name it is for, so a stale one never shows.
  const [answer, setAnswer] = useState({ name: null, available: null });

  useEffect(() => {
    if (!trimmed) return;
    let current = true;
    const timer = setTimeout(() => {
      checkTrackName(trimmed)
        .then((available) => {
          if (current) setAnswer({ name: trimmed, available });
        })
        .catch(() => {
          if (current) setAnswer({ name: trimmed, available: null });
        });
    }, NAME_CHECK_DELAY_MS);
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [trimmed]);

  let status;
  if (!trimmed) status = "idle";
  else if (answer.name !== trimmed) status = "checking";
  else if (answer.available === true) status = "available";
  else if (answer.available === false) status = "taken";
  else status = "unknown";

  const markTaken = () => setAnswer({ name: trimmed, available: false });

  return { status, markTaken };
};

const NAME_STATUS = {
  checking: { text: "Checking…", className: "vibrant-secondary" },
  available: { text: "Name available", className: "text-emerald-600 dark:text-emerald-400" },
  taken: { text: "Name already taken", className: "text-destructive" },
  // Saving still works: the server checks the name again.
  unknown: {
    text: "Couldn't check this name right now",
    className: "vibrant-secondary",
  },
};

const AddTrackDialog = ({ open, onOpenChange, onCreated }) => {
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [pending, setPending] = useState(false);
  const nameCheck = useNameAvailability(form.name);

  const handleOpenChange = (next) => {
    // Closing mid-upload would drop the result on the floor.
    if (pending) return;
    if (!next) {
      setForm(emptyForm());
      setErrors({});
    }
    onOpenChange(next);
  };

  const updateTopic = (id, topic) =>
    setForm((f) => ({
      ...f,
      topics: f.topics.map((t) => (t.id === id ? topic : t)),
    }));

  const removeTopic = (id) =>
    setForm((f) => ({ ...f, topics: f.topics.filter((t) => t.id !== id) }));

  const addTopic = () =>
    setForm((f) => ({ ...f, topics: [...f.topics, emptyTopic()] }));

  const validate = () => {
    const next = {};
    if (!form.name.trim()) next.name = "Give the track a name.";
    else if (nameCheck.status === "taken")
      next.name = "That name is already taken. Try another.";
    form.topics.forEach((topic) => {
      if (!topic.name.trim()) next[topic.id] = "Name this topic.";
    });
    return next;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length) return;

    setPending(true);
    try {
      const track = await createTrack({
        name: form.name.trim(),
        description: form.description.trim(),
        visibility: form.visibility,
        topics: form.topics.map((t) => ({ name: t.name.trim(), files: t.files })),
      });
      setPending(false);
      setForm(emptyForm());
      setErrors({});
      onOpenChange(false);
      onCreated?.(track);
    } catch (cause) {
      setPending(false);
      // Someone may have taken the name between the check and the save.
      if (cause.status === 409 && cause.fieldErrors?.name) {
        nameCheck.markTaken();
        setErrors({ name: "That name is already taken. Try another." });
        return;
      }
      setErrors({
        form:
          cause.status === 413
            ? "These files are too large together. Keep each under 10MB."
            : cause.message,
      });
    }
  };

  return (
    <Dialog.Root open={open} onOpenChange={handleOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/30 transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0 supports-backdrop-filter:backdrop-blur-xs" />
        <Dialog.Popup className="fixed top-1/2 left-1/2 z-50 flex max-h-[calc(100dvh-2rem)] w-[calc(100vw-2rem)] max-w-3xl -translate-x-1/2 -translate-y-1/2 flex-col rounded-2xl border bg-popover text-popover-foreground shadow-lg transition duration-150 data-ending-style:scale-95 data-ending-style:opacity-0 data-starting-style:scale-95 data-starting-style:opacity-0">
          <form
            onSubmit={handleSubmit}
            noValidate
            className="flex min-h-0 flex-1 flex-col"
          >
            <header className="flex items-center justify-between px-6 pt-6 pb-4">
              <Dialog.Title className="text-xl font-semibold">
                Add track
              </Dialog.Title>
              <Dialog.Close
                render={<Button variant="ghost" size="icon-sm" />}
                disabled={pending}
              >
                <XIcon />
                <span className="sr-only">Close</span>
              </Dialog.Close>
            </header>

            <div className="min-h-0 flex-1 overflow-y-auto px-6">
              <div className="grid gap-4 sm:grid-cols-[1fr_auto] sm:items-end">
                <div className="grid gap-2">
                  <Label htmlFor="track-name">Track name</Label>
                  <Input
                    id="track-name"
                    value={form.name}
                    onChange={(event) => {
                      setForm((f) => ({ ...f, name: event.target.value }));
                      setErrors((e) => ({ ...e, name: undefined }));
                    }}
                    placeholder="e.g. Backend fundamentals"
                    maxLength={255}
                    aria-invalid={
                      Boolean(errors.name) || nameCheck.status === "taken"
                    }
                    aria-describedby="track-name-status"
                    autoFocus
                  />
                  <p
                    id="track-name-status"
                    aria-live="polite"
                    className={`min-h-4 text-xs ${
                      errors.name
                        ? "text-destructive"
                        : (NAME_STATUS[nameCheck.status]?.className ?? "")
                    }`}
                  >
                    {errors.name ?? NAME_STATUS[nameCheck.status]?.text}
                  </p>
                </div>

                <VisibilityToggle
                  value={form.visibility}
                  onChange={(visibility) =>
                    setForm((f) => ({ ...f, visibility }))
                  }
                />
              </div>

              <DescriptionField
                value={form.description}
                onChange={(description) =>
                  setForm((f) => ({ ...f, description }))
                }
              />

              <DayColumnHeadings />
              <ol className="mt-4 sm:mt-0">
                {form.topics.map((topic, index) => (
                  <TopicRow
                    key={topic.id}
                    day={index + 1}
                    topic={topic}
                    error={errors[topic.id]}
                    canRemove={form.topics.length > 1}
                    onChange={(next) => updateTopic(topic.id, next)}
                    onRemove={() => removeTopic(topic.id)}
                  />
                ))}
              </ol>

              <div className="flex justify-end border-t pt-4 pb-2">
                <Button type="button" variant="outline" onClick={addTopic}>
                  <PlusIcon />
                  Add topic
                </Button>
              </div>
            </div>

            <footer className="flex items-center gap-3 border-t px-6 py-4">
              {errors.form && (
                <p role="alert" className="text-sm text-destructive">
                  {errors.form}
                </p>
              )}
              <Button type="submit" className="ml-auto" disabled={pending}>
                {pending ? "Saving…" : "Save track"}
              </Button>
            </footer>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
};

export default AddTrackDialog;
