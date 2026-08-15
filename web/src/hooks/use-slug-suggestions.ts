"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { slugApi } from "@/api/slugs";
import type { SlugCandidate } from "@/types/api";
import type { SlugType } from "@/types/enums";

export type SlugSuggestStatus = "idle" | "checking" | "ready" | "error";

/** A validated custom-slug candidate plus its numbered variants. */
export interface SlugCheck {
  candidate: SlugCandidate;
  suggestions: SlugCandidate[];
}

/** Name-derived suggestions fire 3s after the user stops typing the name. */
const NAME_DEBOUNCE_MS = 3000;
/** Live validation of a user-typed custom slug. */
const SLUG_DEBOUNCE_MS = 400;

interface UseSlugSuggestionsOptions {
  type: SlugType;
  platformSlug?: string;
}

/**
 * Availability + suggestions for one slug field. Two independent, debounced
 * triggers feed two separate result slots:
 * - {@link onName} derives the suggested slugs from the display name (slow);
 *   the field auto-selects the first available one, so no re-validation.
 * - {@link onSlug} validates a user-typed custom slug and returns its numbered
 *   variants when taken.
 * Both race-guard so only the latest response wins.
 */
export function useSlugSuggestions({ type, platformSlug }: UseSlugSuggestionsOptions) {
  const [status, setStatus] = useState<SlugSuggestStatus>("idle");
  const [nameSuggestions, setNameSuggestions] = useState<SlugCandidate[]>([]);
  const [slugCheck, setSlugCheck] = useState<SlugCheck | null>(null);
  const requestId = useRef(0);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const run = useCallback(
    (name: string, slug: string, delayMs: number) => {
      const id = ++requestId.current;
      if (timer.current) clearTimeout(timer.current);
      if (!name.trim() && !slug.trim()) {
        setStatus("idle");
        setNameSuggestions([]);
        setSlugCheck(null);
        return;
      }
      setStatus("checking");
      timer.current = setTimeout(async () => {
        try {
          const res = await slugApi.suggest({
            type,
            name: name.trim() || undefined,
            slug: slug.trim() || undefined,
            platformSlug: type === "ORGANISATION" ? platformSlug : undefined,
          });
          if (id === requestId.current) {
            if (slug.trim()) {
              setSlugCheck({ candidate: res.candidate, suggestions: res.suggestions });
            } else {
              setNameSuggestions(res.suggestions);
            }
            setStatus("ready");
          }
        } catch {
          if (id === requestId.current) setStatus("error");
        }
      }, delayMs);
    },
    [type, platformSlug],
  );

  const onName = useCallback((name: string) => run(name, "", NAME_DEBOUNCE_MS), [run]);
  const onSlug = useCallback((slug: string) => run("", slug, SLUG_DEBOUNCE_MS), [run]);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
      requestId.current++;
    },
    [],
  );

  return { status, nameSuggestions, slugCheck, onName, onSlug };
}
