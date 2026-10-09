// Astra Link relay for VectorAgentPhone.
// Deploy as a Cloudflare Worker with a Durable Object binding named ASTRA_QUEUE.
// The relay intentionally stores only a small command/result queue per device.
//
// POST /v1/command
//   Authorization: Bearer <TOKEN>
//   {"device_id":"...","command":{"action":"OPEN_URL","target":"https://...","value":"","task_id":"task-123","click_limit":10,"requires_confirmation":false}}
//
// GET /v1/next?device_id=...
//   Authorization: Bearer <TOKEN>
//
// POST /v1/result
//   Authorization: Bearer <TOKEN>
//   {"device_id":"...","command_id":"...","ok":true,"result":"..."}

export default {
  async fetch(request, env) {
    const auth = request.headers.get("Authorization") || "";
    const token = env.ASTRA_TOKEN || "";
    if (!token || auth !== `Bearer ${token}`) {
      return json({error:"unauthorized"}, 401);
    }

    const url = new URL(request.url);
    if (!url.pathname.startsWith("/v1/")) return json({error:"not_found"}, 404);

    // A Request body can be consumed only once. Parse it here and forward that
    // same parsed object to the Durable Object rather than reading it twice.
    const body = request.method === "GET" ? {} : await safeJson(request);
    const deviceId = url.searchParams.get("device_id") || body.device_id || null;
    if (!deviceId) return json({error:"device_id_required"}, 400);

    const id = env.ASTRA_QUEUE.idFromName(deviceId);
    const stub = env.ASTRA_QUEUE.get(id);
    return stub.fetch(new Request(request.url, {
      method: request.method,
      headers: request.headers,
      body: request.method === "GET" ? undefined : JSON.stringify(body),
    }));
  }
};

export class AstraQueue {
  constructor(state, env) {
    this.state = state;
    this.env = env;
  }

  async fetch(request) {
    const url = new URL(request.url);
    // Do not consume request.json() twice: route handlers below use this object.
    const body = request.method === "GET" ? {} : await safeJson(request);
    const deviceId = url.searchParams.get("device_id") || body.device_id || "";
    if (!deviceId) return json({error:"device_id_required"}, 400);
    const key = "queue:" + deviceId;

    if (request.method === "GET" && url.pathname === "/v1/next") {
      const q = (await this.state.storage.get(key)) || [];
      if (!q.length) return new Response(null, {status:204});
      const item = q.shift();
      await this.state.storage.put(key, q);
      return json(item);
    }

    if (request.method === "POST" && url.pathname === "/v1/command") {
      if (!body.command || !body.command.action) {
        return json({error:"command_required"}, 400);
      }
      const requestedLimit = Number(body.command.click_limit || 0);
      const item = {
        command_id: crypto.randomUUID(),
        created_at: new Date().toISOString(),
        device_id: deviceId,
        action: body.command.action,
        target: body.command.target || "",
        value: body.command.value || "",
        task_id: typeof body.command.task_id === "string" ? body.command.task_id : "",
        click_limit: Number.isFinite(requestedLimit) && requestedLimit > 0
          ? Math.min(9999, Math.floor(requestedLimit)) : 0,
        requires_confirmation: body.command.requires_confirmation !== false
      };
      const q = (await this.state.storage.get(key)) || [];
      if (q.length >= 20) return json({error:"queue_full"}, 429);
      q.push(item);
      await this.state.storage.put(key, q);
      return json(item, 202);
    }

    if (request.method === "POST" && url.pathname === "/v1/result") {
      const commandId = body.command_id || crypto.randomUUID();
      await this.state.storage.put("result:" + commandId, {
        ...body, received_at: new Date().toISOString()
      });
      return json({ok:true, command_id:commandId});
    }

    if (request.method === "GET" && url.pathname === "/v1/result") {
      const commandId = url.searchParams.get("command_id");
      if (!commandId) return json({error:"command_id_required"}, 400);
      const result = await this.state.storage.get("result:" + commandId);
      return result ? json(result) : new Response(null, {status:204});
    }

    return json({error:"not_found"}, 404);
  }
}

async function safeJson(request) {
  try { return await request.json(); } catch (_) { return {}; }
}

function json(value, status=200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {"Content-Type":"application/json; charset=utf-8"}
  });
}
