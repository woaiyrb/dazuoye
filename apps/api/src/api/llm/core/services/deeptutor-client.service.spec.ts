import { AddressInfo } from "node:net";
import { WebSocketServer } from "ws";
import { DeepTutorClientService } from "./deeptutor-client.service";

const logger = {
  child: jest.fn().mockReturnValue({
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
    debug: jest.fn(),
  }),
};

describe("DeepTutorClientService", () => {
  const originalWsUrl = process.env.DEEPTUTOR_WS_URL;
  const originalBaseUrl = process.env.DEEPTUTOR_BASE_URL;
  const originalApiKey = process.env.DEEPTUTOR_API_KEY;
  let server: WebSocketServer;
  let service: DeepTutorClientService;

  beforeEach(async () => {
    server = new WebSocketServer({ port: 0 });
    await new Promise<void>((resolve) => server.once("listening", resolve));
    const address = server.address() as AddressInfo;
    process.env.DEEPTUTOR_WS_URL = `ws://127.0.0.1:${address.port}`;
    process.env.DEEPTUTOR_BASE_URL = `http://127.0.0.1:${address.port}`;
    delete process.env.DEEPTUTOR_API_KEY;
    service = new DeepTutorClientService(logger as never);
  });

  afterEach(async () => {
    for (const client of server.clients) client.terminate();
    await new Promise<void>((resolve) => server.close(() => resolve()));
    restoreEnv("DEEPTUTOR_WS_URL", originalWsUrl);
    restoreEnv("DEEPTUTOR_BASE_URL", originalBaseUrl);
    restoreEnv("DEEPTUTOR_API_KEY", originalApiKey);
  });

  it("sends a v2 turn and accumulates the terminal result", async () => {
    let command: Record<string, unknown> | undefined;
    server.on("connection", (socket) => {
      socket.once("message", (raw) => {
        command = JSON.parse(raw.toString()) as Record<string, unknown>;
        socket.send(JSON.stringify({ type: "session", session_id: "s1" }));
        socket.send(JSON.stringify({ type: "content", content: "第一段，" }));
        socket.send(JSON.stringify({ type: "text", content: "第二段。" }));
        socket.send(
          JSON.stringify({
            type: "result",
            metadata: { summary: { completed: 1 } },
          }),
        );
        socket.send(JSON.stringify({ type: "done" }));
      });
    });

    const result = await service.runTurn(
      {
        content: "请解释勾股定理",
        capability: "chat",
      },
      { purpose: "test", timeoutMs: 3000 },
    );

    expect(command).toMatchObject({
      type: "start_turn",
      protocol_version: "2.0",
      capability: "chat",
      content: "请解释勾股定理",
      persist_user_message: false,
      capability_once: true,
    });
    expect(result.content).toBe("第一段，第二段。");
    expect(result.result?.metadata).toEqual({
      summary: { completed: 1 },
    });
    expect(result.receivedTerminalEvent).toBe(true);
  });

  it("passes the API key as a WebSocket query token", async () => {
    process.env.DEEPTUTOR_API_KEY = "local-test-token";
    let requestUrl = "";
    server.on("connection", (socket, request) => {
      requestUrl = request.url ?? "";
      socket.send(JSON.stringify({ type: "content", content: "ok" }));
      socket.send(JSON.stringify({ type: "done" }));
    });

    await service.runTurn(
      { content: "test", capability: "chat" },
      { purpose: "auth test", timeoutMs: 3000 },
    );

    expect(requestUrl).toContain("/ws?token=local-test-token");
  });

  it("rejects protocol errors instead of returning an empty answer", async () => {
    server.on("connection", (socket) => {
      socket.once("message", () => {
        socket.send(
          JSON.stringify({
            type: "protocol_error",
            error_code: "invalid_command",
            message: "bad command",
          }),
        );
      });
    });

    await expect(
      service.runTurn(
        { content: "test", capability: "chat" },
        { purpose: "error test", timeoutMs: 3000 },
      ),
    ).rejects.toThrow("invalid_command");
  });
});

function restoreEnv(name: string, value: string | undefined): void {
  if (value === undefined) delete process.env[name];
  else process.env[name] = value;
}
