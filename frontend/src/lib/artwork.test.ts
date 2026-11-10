import { describe, expect, it } from "vitest";

import { displaySize } from "@/components/still";

import { atWidth, isSizedPicture, safeLink, safePicture, srcSet, youTubeId } from "./artwork";

const POSTER = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg";
const BACKDROP = "https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK5VQsXEG.jpg";
const ELSEWHERE = "https://example.com/pictures/poster.jpg";

describe("pictures from TMDB", () => {
  it("are offered to the browser at every width they exist at", () => {
    expect(srcSet(POSTER, "poster")).toBe(
      [
        "https://image.tmdb.org/t/p/w185/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg 185w",
        "https://image.tmdb.org/t/p/w342/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg 342w",
        "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg 500w",
        "https://image.tmdb.org/t/p/w780/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg 780w",
      ].join(", "),
    );
  });

  it("offer a still in its original size too, for a screen wider than the largest fixed width", () => {
    expect(srcSet(BACKDROP, "backdrop")).toBe(
      [
        "https://image.tmdb.org/t/p/w780/xJHokMbljvjADYdit5fK5VQsXEG.jpg 780w",
        "https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK5VQsXEG.jpg 1280w",
        "https://image.tmdb.org/t/p/original/xJHokMbljvjADYdit5fK5VQsXEG.jpg 1920w",
      ].join(", "),
    );
  });

  it("can be asked for at another width", () => {
    expect(atWidth(POSTER, 342)).toBe("https://image.tmdb.org/t/p/w342/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg");
    expect(atWidth(atWidth(POSTER, "original"), 185)).toBe(
      "https://image.tmdb.org/t/p/w185/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
    );
  });
});

describe("a picture from anywhere else", () => {
  it("exists at one size: it is used as it is", () => {
    expect(isSizedPicture(ELSEWHERE)).toBe(false);
    expect(atWidth(ELSEWHERE, 342)).toBe(ELSEWHERE);
    expect(srcSet(ELSEWHERE, "poster")).toBeUndefined();
  });

  it("is not mistaken for TMDB's because the address looks alike", () => {
    for (const address of [
      "https://image.tmdb.org.evil.example/t/p/w500/a.jpg",
      "https://evil.example/?https://image.tmdb.org/t/p/w500/a.jpg",
      "http://image.tmdb.org/t/p/w500/a.jpg",
    ]) {
      expect(isSizedPicture(address), address).toBe(false);
    }
  });
});

describe("what is allowed onto a page", () => {
  it("is an https address", () => {
    expect(safePicture(POSTER)).toBe(POSTER);
    expect(safeLink("https://www.youtube.com/watch?v=zSWdZVtXT7E")).toBe("https://www.youtube.com/watch?v=zSWdZVtXT7E");
  });

  it("is nothing else, whatever got into the catalog", () => {
    for (const address of [
      "javascript:alert(1)",
      "data:image/png;base64,AAAA",
      "http://example.com/poster.jpg",
      "//example.com/poster.jpg",
      'https://example.com/a.jpg" onerror="alert(1)',
      "https://example.com/a b.jpg",
      "",
      null,
      undefined,
    ]) {
      expect(safePicture(address), String(address)).toBeNull();
      expect(safeLink(address), String(address)).toBeNull();
    }
  });
});

describe("the trailer that plays on the page", () => {
  it("is found in the address of a YouTube page, in any of the forms it comes in", () => {
    expect(youTubeId("https://www.youtube.com/watch?v=zSWdZVtXT7E")).toBe("zSWdZVtXT7E");
    expect(youTubeId("https://youtube.com/watch?v=wbMNy_djxGk&t=12")).toBe("wbMNy_djxGk");
    expect(youTubeId("https://m.youtube.com/watch?v=R-GU5SymkeM")).toBe("R-GU5SymkeM");
    expect(youTubeId("https://youtu.be/jz8pLlPhSeY")).toBe("jz8pLlPhSeY");
  });

  it("is not made out of anything else: such a trailer stays a plain link", () => {
    for (const address of [
      "https://vimeo.com/123456789",
      "http://www.youtube.com/watch?v=zSWdZVtXT7E",
      "https://www.youtube.com.evil.example/watch?v=zSWdZVtXT7E",
      "https://evil.example/watch?v=zSWdZVtXT7E",
      "https://www.youtube.com/watch",
      "https://www.youtube.com/watch?v=",
      "https://www.youtube.com/watch?v=a/b?c=d&autoplay=1",
      'https://www.youtube.com/watch?v=abc"><script>',
      "https://youtu.be/",
      "https://youtu.be/abcdef/extra",
      "not an address",
    ]) {
      expect(youTubeId(address), address).toBeNull();
    }
  });
});

describe("the size a title is set at", () => {
  it("shrinks as the title grows, so that it never needs more than two lines", () => {
    expect(displaySize("Dune")).toBe("display-short");
    expect(displaySize("Interstellar")).toBe("display-short");
    expect(displaySize("Blade Runner 2049")).toBe("display-medium");
    expect(displaySize("The Grand Budapest Hotel")).toBe("display-medium");
    expect(displaySize("Mission: Impossible, The Final Reckoning")).toBe("display-long");
  });
});
