import { useRef, useState } from "react";
import { FileIcon, PaperclipIcon, Trash2Icon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { MAX_RESOURCE_BYTES, RESOURCE_ACCEPT } from "@/lib/tracks";

/** Matches @Size on the description in CreateFullTrackRequest and EditTrackRequest. */
export const DESCRIPTION_MAX = 2000;

const ALLOWED_EXTENSIONS = RESOURCE_ACCEPT.split(",");

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
const fileKey = (name, size) => `${name}|${size}`;

const formatSize = (bytes) =>
  bytes < 1024 * 1024
    ? `${Math.max(1, Math.round(bytes / 1024))} KB`
    : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

const FileChip = ({ name, size, onRemove }) => (
  <li className="flex items-center gap-2 rounded-lg border px-2 py-1 text-xs">
    <FileIcon className="size-3.5 shrink-0 vibrant-secondary" />
    <span className="min-w-0 flex-1 truncate">{name}</span>
    <span className="shrink-0 vibrant-secondary">{formatSize(size)}</span>
    <button
      type="button"
      onClick={onRemove}
      className="shrink-0 rounded p-0.5 hover:bg-muted"
      aria-label={`Remove ${name}`}
    >
      <XIcon className="size-3" />
    </button>
  </li>
);

/**
 * One day of a track form: the topic name, its files and a remove button.
 *
 * topic.files holds new File objects to upload. topic.keptFiles (edit only)
 * holds files already on the server, as { fileId, originalFilename, sizeBytes }.
 */
export const TopicRow = ({ day, topic, error, canRemove, onChange, onRemove }) => {
  const fileInput = useRef(null);
  const [fileError, setFileError] = useState(null);
  const keptFiles = topic.keptFiles ?? [];

  const addFiles = (event) => {
    const picked = Array.from(event.target.files ?? []);
    // Clear it so picking the same file again still fires a change.
    event.target.value = "";
    const reasons = [];
    const accepted = [];
    const seen = new Set([
      ...keptFiles.map((f) => fileKey(f.originalFilename, f.sizeBytes)),
      ...topic.files.map((f) => fileKey(f.name, f.size)),
    ]);
    for (const file of picked) {
      const reason = rejectReason(file);
      if (reason) {
        reasons.push(reason);
      } else if (seen.has(fileKey(file.name, file.size))) {
        reasons.push(`${file.name}: already added to day ${day}.`);
      } else {
        seen.add(fileKey(file.name, file.size));
        accepted.push(file);
      }
    }
    setFileError(reasons.length ? reasons.join(" ") : null);
    if (accepted.length)
      onChange({ ...topic, files: [...topic.files, ...accepted] });
  };

  const inputId = `topic-${topic.id}`;
  const hasFiles = keptFiles.length > 0 || topic.files.length > 0;

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
        {hasFiles && (
          <ul className="grid gap-1">
            {keptFiles.map((file) => (
              <FileChip
                key={`kept-${file.fileId}`}
                name={file.originalFilename}
                size={file.sizeBytes}
                onRemove={() =>
                  onChange({
                    ...topic,
                    keptFiles: keptFiles.filter((f) => f.fileId !== file.fileId),
                  })
                }
              />
            ))}
            {topic.files.map((file, index) => (
              <FileChip
                key={`${file.name}-${file.lastModified}-${index}`}
                name={file.name}
                size={file.size}
                onRemove={() =>
                  onChange({
                    ...topic,
                    files: topic.files.filter((_, i) => i !== index),
                  })
                }
              />
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

/** The Private / Public switch. */
export const VisibilityToggle = ({ value, onChange }) => (
  <div
    role="radiogroup"
    aria-label="Visibility"
    className="inline-flex h-8 rounded-lg border p-0.5 text-sm"
  >
    {[
      ["PRIVATE", "Private"],
      ["PUBLIC", "Public"],
    ].map(([option, label]) => (
      <button
        key={option}
        type="button"
        role="radio"
        aria-checked={value === option}
        onClick={() => onChange(option)}
        className="rounded-md px-3 transition-colors aria-checked:bg-foreground aria-checked:text-background"
      >
        {label}
      </button>
    ))}
  </div>
);

/** The optional description, with a character count. */
export const DescriptionField = ({ value, onChange }) => (
  <div className="mt-4 grid gap-2">
    <Label htmlFor="track-description">
      Description
      <span className="font-normal vibrant-secondary">(optional)</span>
    </Label>
    <textarea
      id="track-description"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder="What will someone learn by following this track?"
      rows={3}
      maxLength={DESCRIPTION_MAX}
      className="min-h-20 w-full resize-y rounded-lg border border-input bg-transparent px-2.5 py-2 text-base transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm dark:bg-input/30"
    />
    <p className="text-right text-xs vibrant-secondary">
      {value.length}/{DESCRIPTION_MAX}
    </p>
  </div>
);

/** The column headings above the day rows, on wide screens. */
export const DayColumnHeadings = () => (
  <div className="mt-6 hidden grid-cols-[4rem_1fr_1fr] gap-4 pb-2 text-xs font-medium vibrant-secondary sm:grid">
    <span>Day</span>
    <span>Topic name</span>
    <span>Resources</span>
  </div>
);
