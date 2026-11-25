import { useState } from "react";
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
import { updateTrack } from "@/lib/tracks";

let nextRowId = 0;

/** The form, filled from the track as the server has it now. */
const formFrom = (track) => ({
  description: track.description ?? "",
  visibility: track.visibility,
  topics: track.days.map((day) => ({
    id: nextRowId++,
    topicId: day.topicId,
    name: day.topicName,
    keptFiles: day.files,
    files: [],
  })),
});

const newDay = () => ({
  id: nextRowId++,
  topicId: null,
  name: "",
  keptFiles: [],
  files: [],
});

/** Whether the form removes any day or file that exists on the server now. */
const removesSomething = (track, form) => {
  const kept = new Map(
    form.topics.filter((t) => t.topicId).map((t) => [t.topicId, t]),
  );
  return track.days.some((day) => {
    const topic = kept.get(day.topicId);
    return !topic || topic.keptFiles.length < day.files.length;
  });
};

/**
 * Lets the owner change a track's description, visibility, days and files.
 * The name is shown but cannot change. One Save sends everything.
 */
const EditTrackDialog = ({ track, open, onOpenChange, onSaved }) => {
  const [form, setForm] = useState(() => formFrom(track));
  const [errors, setErrors] = useState({});
  const [pending, setPending] = useState(false);

  const handleOpenChange = (next) => {
    // Closing mid-upload would drop the result on the floor.
    if (pending) return;
    if (!next) {
      setForm(formFrom(track));
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
    setForm((f) => ({ ...f, topics: [...f.topics, newDay()] }));

  const validate = () => {
    const next = {};
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
      await updateTrack(track.trackId, {
        description: form.description.trim(),
        visibility: form.visibility,
        days: form.topics.map((t) => ({ ...t, name: t.name.trim() })),
      });
      setPending(false);
      setErrors({});
      onOpenChange(false);
      onSaved?.();
    } catch (cause) {
      setPending(false);
      setErrors({
        form:
          cause.status === 413
            ? "These files are too large together. Keep each under 10MB."
            : cause.message,
      });
    }
  };

  const warnRemoval = removesSomething(track, form);

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
                Edit track
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
                  <Label htmlFor="edit-track-name">Track name</Label>
                  <Input
                    id="edit-track-name"
                    value={track.name}
                    disabled
                    aria-describedby="edit-track-name-note"
                  />
                  <p
                    id="edit-track-name-note"
                    className="min-h-4 text-xs vibrant-secondary"
                  >
                    The track name cannot change.
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

            <footer className="flex flex-wrap items-center gap-3 border-t px-6 py-4">
              <div className="grid min-w-0 flex-1 gap-1">
                {warnRemoval && (
                  <p className="text-sm text-destructive">
                    Removed days and files are deleted when you save. You
                    cannot undo this.
                  </p>
                )}
                {errors.form && (
                  <p role="alert" className="text-sm text-destructive">
                    {errors.form}
                  </p>
                )}
              </div>
              <Button type="submit" className="ml-auto" disabled={pending}>
                {pending ? "Saving…" : "Save changes"}
              </Button>
            </footer>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
};

export default EditTrackDialog;
