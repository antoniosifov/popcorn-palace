"use client";

import { useRef, useState } from "react";

import { youTubeId } from "@/lib/artwork";

type Props = {
  title: string;
  /** The https address of the trailer, already checked by `safeLink`. */
  url: string;
};

/**
 * The trailer plays on the page, over it, instead of sending the visitor away
 * before they have bought a ticket. Nothing is asked of YouTube until the
 * visitor asks for the trailer: the player is only put on the page when it is
 * opened, from the address that sets no cookies before play.
 *
 * It is a plain link underneath: without scripts, with a middle click, or for
 * a trailer that is not on YouTube, it opens the trailer where it lives.
 */
export function Trailer({ title, url }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [playing, setPlaying] = useState(false);
  const video = youTubeId(url);

  if (!video) {
    return (
      <a href={url} target="_blank" rel="noreferrer" className="link">
        Watch the trailer
      </a>
    );
  }

  return (
    <>
      <a
        href={url}
        target="_blank"
        rel="noreferrer"
        className="link"
        aria-haspopup="dialog"
        onClick={(event) => {
          if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) {
            return;
          }
          event.preventDefault();
          setPlaying(true);
          dialog.current?.showModal();
        }}
      >
        Watch the trailer
      </a>
      <dialog
        ref={dialog}
        className="trailer"
        aria-label={`Trailer: ${title}`}
        onClose={() => setPlaying(false)}
        onClick={(event) => {
          // A press on the dark around the picture closes it; a press on the picture does not.
          if (event.target === dialog.current) {
            dialog.current.close();
          }
        }}
      >
        <div className="trailer-frame">
          {playing ? (
            <iframe
              src={`https://www.youtube-nocookie.com/embed/${video}?autoplay=1&rel=0`}
              title={`Trailer: ${title}`}
              allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
              referrerPolicy="strict-origin-when-cross-origin"
              allowFullScreen
            />
          ) : null}
        </div>
        <button
          type="button"
          className="btn btn-secondary mt-4 justify-self-end"
          onClick={() => dialog.current?.close()}
        >
          Close
        </button>
      </dialog>
    </>
  );
}
