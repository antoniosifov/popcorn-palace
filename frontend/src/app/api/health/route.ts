/** Answers the container health check: the server is up and serving. */
export function GET() {
  return Response.json({ status: "UP" });
}
