import path from "node:path";

import type { NextConfig } from "next";

// This folder is the whole application: nothing above it belongs to the build.
const root = path.resolve(__dirname);

const nextConfig: NextConfig = {
  // Pages are a static shell plus content that streams in at request time:
  // everything the API returns is fetched per request, behind <Suspense>.
  cacheComponents: true,
  partialPrefetching: true,
  // A self-contained server in .next/standalone, which is what the Docker image runs.
  output: "standalone",
  outputFileTracingRoot: root,
  // The API is the public face of errors; do not advertise the framework.
  poweredByHeader: false,
  turbopack: {
    root,
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
