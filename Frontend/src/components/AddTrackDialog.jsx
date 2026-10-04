import { useEffect, useRef, useState } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { FileIcon, PaperclipIcon, PlusIcon, Trash2Icon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  MAX_RESOURCE_BYTES,
  RESOURCE_ACCEPT,
  checkTrackName,
  createTrack,
} from "@/lib/tracks";

const NAME_CHECK_DELAY_MS = 300;
/** Matches @Size on CreateFullTrackRequest.description. */
const DESCRIPTION_MAX = 2000;

const ALLOWED_EXTENSIONS = RESOURCE_ACCEPT.split(",");

let nextTopicId = 0;
const emptyTopic = () => ({ id: nextTopicId++, name: "", files: [] });

const emptyForm = () => ({
  name: "",
  description: "",
  visibility: "PRIVATE",
  topics: [emptyTopic()],
});

/** Returns why a file cannot be uploaded, or null if it can. */
const rejectReason = (file) => {
  const extension = file.name.slice(file.name.lastIndexOf(".")).toLowerCase();
  if (!ALLOWED_EXTENSIONS.includes(extension))
    return `${file.name}: only PDF, Office, text and image files are allowed.`;
  if (file.size > MAX_RESOURCE_BYTES)
    return `${file.name}: larger than 10MB.`;
  if (file.size === 0) return `${file.name}: the file is empty.`;
  return null;
};

/** Name and size: what the server can also see, so both sides agree. */
const fileKey = (file) => `${file.name}|${file.size}`;

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

const formatSize = (bytes) =>
  bytes < 1024 * 1024
    ? `${Math.max(1, Math.round(bytes / 1024))} KB`
    : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

const TopicRow = ({ day, topic, error, canRemove, onChange, onRemove }) => {
  const fileInput = useRef(null);
  const [fileError, setFileError] = useState(null);

  const addFiles = (event) => {
    const picked = Array.from(event.target.files ?? []);
    // Clear it so picking the same file again still fires a change.
    event.target.value = "";
    const reasons = [];
    const accepted = [];
    const seen = new Set(topic.files.map(fileKey));
    for (const file of picked) {
      const reason = rejectReason(file);
      if (reason) {
        reasons.push(reason);
      } else if (seen.has(fileKey(file))) {
        reasons.push(`${file.name}: already added to day ${day}.`);
      } else {
        seen.add(fileKey(file));
        accepted.push(file);
      }
    }
    setFileError(reasons.length ? reasons.join(" ") : null);
    if (accepted.length)
      onChange({ ...topic, files: [...topic.files, ...accepted] });
  };

  const removeFile = (index) =>
    onChange({ ...topic, files: topic.files.filter((_, i) => i !== index) });

  const inputId = `topic-${topic.id}`;

  return (
    <li className="grid gap-3 border-t py-4 sm:grid-cols-[4rem_1fr_1fr] sm:items-start sm:gap-4">
      <span className="text-sm font-medium sm:pt-1.5">Day {day}</span>

      <div className="grid gap-1.5">
        <Label htmlFor={inputId} className="sr-only">
          Topic name for day {day}
        </Label>
        <Input
          id={inputId}
          value={topic.name}
          onChange={(event) => onChange({ ...topic, name: event.target.value })}
          placeholder="Topic name"
          maxLength={255}
          aria-invalid={Boolean(error)}
        />
        {error && <p className="text-xs text-destructive">{error}</p>}
      </div>

      <div className="grid gap-2">
        {topic.files.length > 0 && (
          <ul className="grid gap-1">
            {topic.files.map((file, index) => (
              <li
                key={`${file.name}-${file.lastModified}-${index}`}
                className="flex items-center gap-2 rounded-lg border px-2 py-1 text-xs"
              >
                <FileIcon className="size-3.5 shrink-0 vibrant-secondary" />
                <span className="min-w-0 flex-1 truncate">{file.name}</span>
                <span className="shrink-0 vibrant-secondary">
                  {formatSize(file.size)}
                </span>
                <button
                  type="button"
                  onClick={() => removeFile(index)}
                  className="shrink-0 rounded p-0.5 hover:bg-muted"
                  aria-label={`Remove ${file.name}`}
                >
                  <XIcon className="size-3" />
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => fileInput.current?.click()}
          >
            <PaperclipIcon />
            Add resources
          </Button>
          {canRemove && (
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={onRemove}
              aria-label={`Remove day ${day}`}
              className="ml-auto"
            >
              <Trash2Icon />
            </Button>
          )}
        </div>
        <input
          ref={fileInput}
          type="file"
          multiple
          accept={RESOURCE_ACCEPT}
          onChange={addFiles}
          className="hidden"
        />
        {fileError && <p className="text-xs text-destructive">{fileError}</p>}
      </div>
    </li>
  );
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

                <div
                  role="radiogroup"
                  aria-label="Visibility"
                  className="inline-flex h-8 rounded-lg border p-0.5 text-sm"
                >
                  {[
                    ["PRIVATE", "Private"],
                    ["PUBLIC", "Public"],
                  ].map(([value, label]) => (
                    <button
                      key={value}
                      type="button"
                      role="radio"
                      aria-checked={form.visibility === value}
                      onClick={() =>
                        setForm((f) => ({ ...f, visibility: value }))
                      }
                      className="rounded-md px-3 transition-colors aria-checked:bg-foreground aria-checked:text-background"
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mt-4 grid gap-2">
                <Label htmlFor="track-description">
                  Description
                  <span className="font-normal vibrant-secondary">
                    (optional)
                  </span>
                </Label>
                <textarea
                  id="track-description"
                  value={form.description}
                  onChange={(event) =>
                    setForm((f) => ({ ...f, description: event.target.value }))
                  }
                  placeholder="What will someone learn by following this track?"
                  rows={3}
                  maxLength={DESCRIPTION_MAX}
                  className="min-h-20 w-full resize-y rounded-lg border border-input bg-transparent px-2.5 py-2 text-base transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm dark:bg-input/30"
                />
                <p className="text-right text-xs vibrant-secondary">
                  {form.description.length}/{DESCRIPTION_MAX}
                </p>
              </div>

              <div className="mt-6 hidden grid-cols-[4rem_1fr_1fr] gap-4 pb-2 text-xs font-medium vibrant-secondary sm:grid">
                <span>Day</span>
                <span>Topic name</span>
                <span>Resources</span>
              </div>
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
