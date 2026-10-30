import { createServer } from "node:net";

export async function findAvailablePort(
  firstCandidate: number,
  host = "127.0.0.1",
  candidateCount = 25,
): Promise<number> {
  if (!Number.isInteger(firstCandidate) || firstCandidate < 1 || firstCandidate > 65535) {
    throw new Error("Starting port must be an integer between 1 and 65535");
  }
  if (!Number.isInteger(candidateCount) || candidateCount < 1) {
    throw new Error("Candidate count must be a positive integer");
  }

  for (let offset = 0; offset < candidateCount; offset += 1) {
    const port = firstCandidate + offset;
    if (port > 65535) break;
    if (await canListen(host, port)) return port;
  }
  throw new Error(
    `No available port found between ${firstCandidate} and ${Math.min(65535, firstCandidate + candidateCount - 1)}`,
  );
}

function canListen(host: string, port: number): Promise<boolean> {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.once("error", error => {
      if ("code" in error && error.code === "EADDRINUSE") resolve(false);
      else reject(error);
    });
    server.listen(port, host, () => {
      server.close(error => error ? reject(error) : resolve(true));
    });
  });
}
