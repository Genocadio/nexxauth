"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Check, CircleAlert, Loader2, Plus, X } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useSlugSuggestions } from "@/hooks/use-slug-suggestions";
import type { SlugType } from "@/types/enums";

interface SlugFieldProps {
  id: string;
  label: string;
  /** Current display-name value; its suggestions appear once it settles. */
  name: string;
  value: string;
  onChange: (value: string) => void;
  type: SlugType;
  platformSlug?: string;
  error?: string;
  hint?: string;
  placeholder?: string;
}

/**
 * Slug field backed by the suggestions endpoint. Hidden until a display name
 * is typed; then it shows the available suggested slugs as clickable chips
 * with the first one auto-selected, plus a "+" to create your own. Picking
 * from the chips never re-validates. In custom mode ("+") the text input
 * appears and the typed slug is validated live, offering numbered variants
 * when taken.
 */
export function SlugField({
  id,
  label,
  name,
  value,
  onChange,
  type,
  platformSlug,
  error,
  hint,
  placeholder,
}: SlugFieldProps) {
  const { status, nameSuggestions, slugCheck, onName, onSlug } = useSlugSuggestions({ type, platformSlug });
  const inputRef = useRef<HTMLInputElement>(null);
  const [custom, setCustom] = useState(false);
  // True once the user has picked a specific slug (chip or "+"); fresh
  // suggestions from an edited name reset it so the 1st is auto-selected again.
  const userChosen = useRef(false);

  const isEmpty = value.trim().length === 0;
  const availableSuggestions = useMemo(
    () => nameSuggestions.filter((s) => s.available),
    [nameSuggestions],
  );

  // Derive suggestions from the name 3s after the user stops typing it — but
  // only while the user is not creating their own slug.
  useEffect(() => {
    if (!custom) onName(name);
  }, [name, custom, onName]);

  // A new display name means new suggestions, so auto-selection applies again.
  useEffect(() => {
    userChosen.current = false;
  }, [name]);

  // Always auto-select the 1st available suggestion (no re-check: suggested
  // slugs are already known to be available) unless the user has explicitly
  // chosen another slug or started creating their own.
  useEffect(() => {
    if (custom || userChosen.current) return;
    const first = availableSuggestions[0];
    if (first && value !== first.slug) onChange(first.slug);
  }, [availableSuggestions, custom, value, onChange]);

  useEffect(() => {
    if (custom) inputRef.current?.focus();
  }, [custom]);

  const select = (slug: string) => {
    setCustom(false);
    userChosen.current = true;
    onChange(slug);
  };

  const createOwn = () => {
    setCustom(true);
    userChosen.current = true;
    onChange("");
  };

  const handleInputChange = (next: string) => {
    onChange(next);
    if (next.trim()) onSlug(next);
  };

  const checked = slugCheck && slugCheck.candidate.slug === value.trim() && !isEmpty;

  // The slug is optional and now suggested: hide the whole field until a
  // display name is typed (or the user is mid-way through creating their own).
  if (!custom && !name.trim()) return null;

  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>

      {custom ? (
        <>
          <Input
            ref={inputRef}
            id={id}
            placeholder={placeholder}
            value={value}
            aria-invalid={error ? true : undefined}
            onChange={(e) => handleInputChange(e.target.value)}
          />
          {status === "checking" ? (
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Loader2 className="size-3 animate-spin" /> Checking availability…
            </p>
          ) : status === "error" ? (
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <CircleAlert className="size-3" /> Couldn&apos;t check availability right now.
            </p>
          ) : null}
          {status === "ready" && checked ? (
            <p
              className={`flex items-center gap-1.5 text-xs font-medium ${
                slugCheck!.candidate.available ? "text-emerald-600 dark:text-emerald-400" : "text-destructive"
              }`}
            >
              {slugCheck!.candidate.available ? <Check className="size-3" /> : <X className="size-3" />}
              {slugCheck!.candidate.slug} is {slugCheck!.candidate.available ? "available" : "taken"}
            </p>
          ) : null}
          {status === "ready" && checked && !slugCheck!.candidate.available ? (
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">Try one of these</p>
              <div className="flex flex-wrap gap-1.5">
                {slugCheck!.suggestions
                  .filter((s) => s.available)
                  .map((s) => (
                    <button
                      key={s.slug}
                      type="button"
                      onClick={() => select(s.slug)}
                      className="inline-flex items-center gap-1 rounded-md border border-primary/40 px-2 py-1 text-xs font-medium text-foreground transition-colors hover:border-primary hover:bg-primary/5"
                    >
                      <Check className="size-3 text-emerald-600 dark:text-emerald-400" />
                      {s.slug}
                    </button>
                  ))}
              </div>
            </div>
          ) : null}
        </>
      ) : (
        <div className="flex flex-wrap items-center gap-1.5">
          {availableSuggestions.map((s) => {
            const selected = s.slug === value;
            return (
              <button
                key={s.slug}
                type="button"
                aria-pressed={selected}
                onClick={() => select(s.slug)}
                className={`inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs font-medium transition-colors ${
                  selected
                    ? "border-primary bg-primary/5 text-foreground"
                    : "border-input text-muted-foreground hover:border-primary/40 hover:text-foreground"
                }`}
              >
                {selected ? <Check className="size-3 text-emerald-600 dark:text-emerald-400" /> : null}
                {s.slug}
              </button>
            );
          })}
          <button
            type="button"
            onClick={createOwn}
            aria-label="Create your own"
            title="Create your own"
            className="inline-flex size-6 items-center justify-center rounded-md border border-dashed border-input text-muted-foreground transition-colors hover:border-primary/40 hover:text-foreground"
          >
            <Plus className="size-3.5" />
          </button>
        </div>
      )}

      {error ? <p className="text-xs font-medium text-destructive">{error}</p> : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}
