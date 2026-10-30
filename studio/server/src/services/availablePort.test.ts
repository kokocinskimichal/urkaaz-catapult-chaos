import { createServer } from "node:net";
import { afterEach, describe, expect, it } from "vitest";
import { findAvailablePort } from "./availablePort.ts";

const servers: ReturnType<typeof createServer>[] = [];

afterEach(async () => {
  await Promise.all(servers.splice(0).map(server =>
    new Promise<void>(resolve => server.close(() => resolve())),
  ));
});

describe("findAvailablePort", () => {
  it("uses the requested port when it is available", async () => {
    const server = createServer();
    servers.push(server);
    const requestedPort = await listenEphemeral(server);
    await new Promise<void>(resolve => server.close(() => resolve()));
    servers.splice(servers.indexOf(server), 1);

    await expect(findAvailablePort(requestedPort)).resolves.toBe(requestedPort);
  });

  it("skips an occupied port and returns a later available one", async () => {
    const server = createServer();
    servers.push(server);
    const occupiedPort = await listenEphemeral(server);

    const selectedPort = await findAvailablePort(occupiedPort, "127.0.0.1", 20);

    expect(selectedPort).toBeGreaterThan(occupiedPort);
    expect(selectedPort).toBeLessThanOrEqual(occupiedPort + 19);
  });

  it("rejects invalid starting ports", async () => {
    await expect(findAvailablePort(0)).rejects.toThrow("between 1 and 65535");
    await expect(findAvailablePort(4310, "127.0.0.1", 0)).rejects.toThrow("positive integer");
  });
});

async function listenEphemeral(server: ReturnType<typeof createServer>): Promise<number> {
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("Expected an ephemeral TCP port");
  return address.port;
}
