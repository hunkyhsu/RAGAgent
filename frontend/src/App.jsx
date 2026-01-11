import React, { useEffect, useMemo, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from "rehype-highlight";
import "highlight.js/styles/github.css";

const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

const jsonHeaders = (token) => {
  const headers = { "Content-Type": "application/json" };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
};

const buildWsUrl = (baseUrl, token, convId) => {
  const url = new URL(baseUrl);
  const protocol = url.protocol === "https:" ? "wss:" : "ws:";
  const wsUrl = new URL(`${protocol}//${url.host}/ws/chat`);
  wsUrl.searchParams.set("token", token);
  if (convId) {
    wsUrl.searchParams.set("convId", String(convId));
  }
  return wsUrl.toString();
};

const createId = () => {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `msg_${Date.now()}_${Math.random().toString(16).slice(2)}`;
};

const useLocalStorageState = (key, initialValue) => {
  const [value, setValue] = useState(() => {
    const stored = localStorage.getItem(key);
    return stored ? JSON.parse(stored) : initialValue;
  });

  useEffect(() => {
    localStorage.setItem(key, JSON.stringify(value));
  }, [key, value]);

  return [value, setValue];
};

const fetchJson = async (path, options = {}, token) => {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...jsonHeaders(token),
      ...(options.headers || {})
    }
  });
  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await response.json() : null;
  if (!response.ok) {
    const error = new Error(body?.message || "Request failed");
    error.code = body?.code;
    throw error;
  }
  return body;
};

const extractCodeText = (children) => {
  if (Array.isArray(children)) {
    return children.join("");
  }
  if (children === null || children === undefined) {
    return "";
  }
  return String(children);
};

const resolveLanguage = (className) => {
  const match = /language-([a-zA-Z0-9_-]+)/.exec(className || "");
  return match ? match[1] : "text";
};

const CodeBlock = ({ inline, className, children, ...props }) => {
  const [copied, setCopied] = useState(false);
  const codeText = extractCodeText(children);
  const language = resolveLanguage(className);

  const handleCopy = async () => {
    if (!codeText) {
      return;
    }
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(codeText);
      } else {
        const textarea = document.createElement("textarea");
        textarea.value = codeText;
        textarea.style.position = "fixed";
        textarea.style.top = "-9999px";
        textarea.style.opacity = "0";
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand("copy");
        document.body.removeChild(textarea);
      }
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch (err) {
      // ignore clipboard failures
    }
  };

  if (inline) {
    return (
      <code className={className} {...props}>
        {children}
      </code>
    );
  }

  return (
    <div className="code-block">
      <div className="code-block__header">
        <span className="code-block__lang">{language}</span>
        <button type="button" className="code-block__copy" onClick={handleCopy}>
          {copied ? "Copied" : "Copy"}
        </button>
      </div>
      <pre className="code-block__pre">
        <code className={className} {...props}>
          {children}
        </code>
      </pre>
    </div>
  );
};

const ChatBubble = ({ role, content, time, streaming }) => {
  const isUser = role === "USER";
  const isSystem = role === "SYSTEM";
  const bubbleStyle = isSystem
    ? "bg-rose-50 border-rose-200 text-rose-700"
    : isUser
    ? "bg-white border-slate-200 text-slate-800"
    : "bg-sky-50 border-sky-200 text-slate-800";

  return (
    <div className={`fade-rise flex flex-col gap-2 ${isUser ? "items-end" : "items-start"}`}>
      <div className={`max-w-[85%] rounded-2xl border px-4 py-3 text-sm leading-relaxed ${bubbleStyle}`}>
        {isUser ? (
          <div className="whitespace-pre-wrap">{content || (streaming ? "..." : "")}</div>
        ) : (
          <ReactMarkdown
            className="markdown"
            remarkPlugins={[remarkGfm]}
            rehypePlugins={[rehypeHighlight]}
            components={{
              pre({ children }) {
                return <>{children}</>;
              },
              code: CodeBlock
            }}
          >
            {content || (streaming ? "..." : "")}
          </ReactMarkdown>
        )}
      </div>
      <div className="text-xs text-slate-400 text-mono">{time}</div>
    </div>
  );
};

const EmptyState = ({ onStart }) => (
    <div className="flex h-full flex-col items-center justify-center gap-6 text-center">
      <div className="max-w-md space-y-3">
      <p className="text-sm uppercase tracking-[0.3em] text-slate-400">Gemini-style MVP</p>
      <h2 className="text-3xl font-semibold text-slate-800">Start a focused conversation</h2>
      <p className="text-sm text-slate-500">
        Create a thread, then stream replies in real time. Your workspace remembers every message.
      </p>
    </div>
    <button
      onClick={onStart}
      className="rounded-full bg-slate-900 px-6 py-3 text-sm font-semibold text-white shadow-[0_10px_30px_rgba(15,23,42,0.2)]"
    >
      New conversation
    </button>
  </div>
);

export default function App() {
  const [token, setToken] = useLocalStorageState("ragagent_token", "");
  const [user, setUser] = useState(null);
  const [authMode, setAuthMode] = useState("login");
  const [authError, setAuthError] = useState("");
  const [authLoading, setAuthLoading] = useState(false);
  const [authForm, setAuthForm] = useState({
    username: "",
    password: "",
    email: "",
    orgTags: ""
  });

  const [conversations, setConversations] = useState([]);
  const [activeConversation, setActiveConversation] = useState(null);
  const [messages, setMessages] = useState([]);
  const [composer, setComposer] = useState("");
  const [wsStatus, setWsStatus] = useState("disconnected");
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
  const [activeMenuId, setActiveMenuId] = useState(null);
  const [showUserMenu, setShowUserMenu] = useState(false);

  const wsRef = useRef(null);
  const sendQueue = useRef([]);
  const scrollRef = useRef(null);

  useEffect(() => {
    if (!token) {
      setUser(null);
      return;
    }
    fetchJson("/api/auth/me", { method: "GET" }, token)
      .then((data) => setUser(data))
      .catch(() => {
        setUser(null);
        setToken("");
      });
  }, [token, setToken]);

  useEffect(() => {
    if (!token) {
      setConversations([]);
      setActiveConversation(null);
      setMessages([]);
      return;
    }
    fetchJson("/api/conversations/list", { method: "GET" }, token)
      .then((data) => setConversations(data || []))
      .catch(() => setConversations([]));
  }, [token]);

  useEffect(() => {
    if (!token || !activeConversation) {
      return;
    }
    fetchJson(`/api/conversations/${activeConversation}/messages`, { method: "GET" }, token)
      .then((data) => {
        const mapped = (data || []).map((msg) => ({
          id: String(msg.id),
          role: msg.role,
          content: msg.content,
          ts: msg.createdTime,
          streaming: false
        }));
        setMessages(mapped);
      })
      .catch(() => setMessages([]));
  }, [token, activeConversation]);

  useEffect(() => {
    if (!token || !activeConversation) {
      return;
    }

    const wsUrl = buildWsUrl(API_BASE, token, activeConversation);
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;
    setWsStatus("connecting");

    ws.onopen = () => {
      setWsStatus("open");
    };
    ws.onclose = () => setWsStatus("closed");
    ws.onerror = () => setWsStatus("error");
    ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        handleEnvelope(payload);
      } catch (err) {
        // ignore malformed payloads
      }
    };

    return () => {
      ws.close();
    };
  }, [token, activeConversation]);

  useEffect(() => {
    if (wsStatus !== "open") {
      return;
    }
    if (!sendQueue.current.length) {
      return;
    }
    const ws = wsRef.current;
    while (ws && ws.readyState === WebSocket.OPEN && sendQueue.current.length) {
      const next = sendQueue.current.shift();
      ws.send(JSON.stringify(next));
    }
  }, [wsStatus]);

  useEffect(() => {
    if (!scrollRef.current) {
      return;
    }
    scrollRef.current.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    const handleClick = () => {
      setActiveMenuId(null);
      setShowUserMenu(false);
    };
    window.addEventListener("click", handleClick);
    return () => window.removeEventListener("click", handleClick);
  }, []);

  const activeConv = useMemo(
    () => conversations.find((conv) => conv.id === activeConversation) || null,
    [conversations, activeConversation]
  );

  const handleEnvelope = (payload) => {
    if (!payload || !payload.type) {
      return;
    }

    if (payload.type === "chat.stream") {
      setMessages((prev) => {
        const idx = prev.findIndex((item) => item.id === payload.messageId && item.role === "ASSISTANT");
        if (idx === -1) {
          return [
            ...prev,
            {
              id: payload.messageId,
              role: "ASSISTANT",
              content: payload.content || "",
              ts: payload.ts,
              streaming: true
            }
          ];
        }
        const next = [...prev];
        const current = next[idx];
        next[idx] = {
          ...current,
          content: `${current.content || ""}${payload.content || ""}`,
          streaming: true
        };
        return next;
      });
      return;
    }

    if (payload.type === "chat.done") {
      setMessages((prev) =>
        prev.map((item) =>
          item.id === payload.messageId && item.role === "ASSISTANT"
            ? { ...item, streaming: false }
            : item
        )
      );
      return;
    }

    if (payload.type === "chat.title") {
      if (!payload.conversationId || !payload.content) {
        return;
      }
      setConversations((prev) =>
        prev.map((item) =>
          item.id === payload.conversationId ? { ...item, title: payload.content } : item
        )
      );
      return;
    }

    if (payload.type === "error") {
      setMessages((prev) => [
        ...prev,
        {
          id: `${payload.messageId || createId()}_sys`,
          role: "SYSTEM",
          content: `Error ${payload.code?.code || ""}: ${payload.code?.message || "unexpected"}`,
          ts: payload.ts,
          streaming: false
        }
      ]);
    }
  };

  const handleAuthChange = (event) => {
    const { name, value } = event.target;
    setAuthForm((prev) => ({ ...prev, [name]: value }));
  };

  const submitAuth = async (event) => {
    event.preventDefault();
    setAuthError("");
    setAuthLoading(true);
    try {
      const payload = {
        username: authForm.username,
        password: authForm.password
      };
      if (authMode === "register") {
        payload.email = authForm.email;
        payload.orgTags = authForm.orgTags;
      }
      const path = authMode === "login" ? "/api/auth/login" : "/api/auth/register";
      const response = await fetchJson(path, {
        method: "POST",
        body: JSON.stringify(payload)
      });
      setToken(response.accessToken || "");
      setAuthForm({ username: "", password: "", email: "", orgTags: "" });
    } catch (err) {
      const rawMessage = err?.message || "";
      if (rawMessage.toLowerCase().startsWith("size must be between")) {
        setAuthError("password must be between 8 and 128 characters");
      } else {
        setAuthError(rawMessage || "Login failed");
      }
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      await fetchJson("/api/auth/logout", { method: "POST" }, token);
    } catch (err) {
      // ignore
    }
    setToken("");
    setUser(null);
  };

  const createConversation = async (title) => {
    const response = await fetchJson(
      "/api/conversations/create",
      {
        method: "POST",
        body: JSON.stringify({ title })
      },
      token
    );
    setConversations((prev) => [response, ...prev]);
    setActiveConversation(response.id);
    return response.id;
  };

  const sendMessage = async () => {
    const trimmed = composer.trim();
    if (!trimmed) {
      return;
    }
    let convId = activeConversation;
    if (!convId) {
      convId = await createConversation("New Chat");
    }

    const messageId = createId();
    setMessages((prev) => [
      ...prev,
      { id: messageId, role: "USER", content: trimmed, ts: Date.now(), streaming: false }
    ]);
    setComposer("");

    const envelope = {
      type: "chat.send",
      conversationId: convId,
      messageId,
      role: "USER",
      content: trimmed,
      ts: Date.now()
    };

    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(envelope));
    } else {
      sendQueue.current.push(envelope);
    }
  };

  const pendingLabel = useMemo(() => {
    if (wsStatus === "open") return "Live";
    if (wsStatus === "connecting") return "Connecting";
    if (wsStatus === "error") return "Offline";
    return "Idle";
  }, [wsStatus]);
  const isLive = wsStatus === "open";

  const handleRenameConversation = async (conv) => {
    const nextTitle = window.prompt("Rename conversation", conv.title || "");
    if (nextTitle === null) {
      return;
    }
    const trimmed = nextTitle.trim();
    if (!trimmed) {
      return;
    }
    try {
      const updated = await fetchJson(
        `/api/conversations/${conv.id}`,
        {
          method: "PUT",
          body: JSON.stringify({ title: trimmed })
        },
        token
      );
      setConversations((prev) =>
        prev.map((item) => (item.id === conv.id ? { ...item, title: updated.title } : item))
      );
    } catch (err) {
      // ignore
    } finally {
      setActiveMenuId(null);
    }
  };

  const handleDeleteConversation = async (conv) => {
    try {
      await fetchJson(`/api/conversations/${conv.id}`, { method: "DELETE" }, token);
      setConversations((prev) => prev.filter((item) => item.id !== conv.id));
      if (activeConversation === conv.id) {
        setActiveConversation(null);
        setMessages([]);
      }
    } catch (err) {
      // ignore
    } finally {
      setActiveMenuId(null);
    }
  };

  if (!token) {
    return (
      <div className="min-h-screen px-6 py-10 md:px-12">
        <div className="mx-auto grid max-w-6xl gap-10 md:grid-cols-[1.2fr_1fr]">
          <div className="flex flex-col gap-6 pt-6">
            <p className="text-sm uppercase tracking-[0.5em] text-slate-400">RagAgent</p>
            <h1 className="text-4xl font-semibold text-slate-900 md:text-5xl">A Gemini-inspired chat cockpit</h1>
            <p className="max-w-lg text-base text-slate-600">
              Stream responses, keep conversations organized, and stay inside a focused AI workspace built for speed.
            </p>
            <div className="grid gap-4 sm:grid-cols-2">
              {[
                "Streaming responses with real time updates",
                "Conversation history persists automatically",
                "Token-authenticated sessions with rate limits",
                "Minimal, sharp UI optimized for focus"
              ].map((item) => (
                <div key={item} className="glass rounded-2xl px-4 py-3 text-sm text-slate-700">
                  {item}
                </div>
              ))}
            </div>
          </div>

          <div className="glass-strong rounded-3xl p-8 shadow-2xl">
            <div className="flex items-center justify-between">
              <h2 className="text-2xl font-semibold text-slate-900">
                {authMode === "login" ? "Sign in" : "Create account"}
              </h2>
              <button
                className="text-sm text-slate-600 hover:text-slate-800"
                onClick={() => {
                  setAuthMode(authMode === "login" ? "register" : "login");
                  setAuthError("");
                }}
              >
                {authMode === "login" ? "Need an account?" : "Already have one?"}
              </button>
            </div>
            <form className="mt-6 grid gap-4" onSubmit={submitAuth}>
              <label className="grid gap-2 text-sm">
                Username
                <input
                  className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 outline-none focus:border-slate-500"
                  name="username"
                  value={authForm.username}
                  onChange={handleAuthChange}
                  required
                />
              </label>
              {authMode === "register" && (
                <label className="grid gap-2 text-sm">
                  Email
                  <input
                    className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 outline-none focus:border-slate-500"
                    name="email"
                    type="email"
                    value={authForm.email}
                    onChange={handleAuthChange}
                    required
                  />
                </label>
              )}
              <label className="grid gap-2 text-sm">
                Password
                <input
                  className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 outline-none focus:border-slate-500"
                  name="password"
                  type="password"
                  value={authForm.password}
                  onChange={handleAuthChange}
                  required
                />
              </label>
              {authMode === "register" && (
                <label className="grid gap-2 text-sm">
                  Org tags (optional)
                  <input
                    className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 outline-none focus:border-slate-500"
                    name="orgTags"
                    value={authForm.orgTags}
                    onChange={handleAuthChange}
                  />
                </label>
              )}
              {authError && <p className="text-sm text-rose-600">{authError}</p>}
              <button
                type="submit"
                disabled={authLoading}
                className="mt-2 rounded-xl bg-slate-900 px-4 py-3 text-sm font-semibold text-white disabled:opacity-60"
              >
                {authLoading ? "Please wait..." : authMode === "login" ? "Login" : "Register"}
              </button>
            </form>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen flex-col gap-4 overflow-hidden px-4 py-4 md:flex-row md:px-6">
      <aside
        className={`glass flex h-full flex-col rounded-3xl p-4 transition-all duration-200 ${
          isSidebarCollapsed ? "w-16" : "w-full md:w-72"
        }`}
      >
        <div className={`flex items-center ${isSidebarCollapsed ? "justify-center" : "justify-between"}`}>
          <button
            type="button"
            title="close sidebar"
            onClick={(event) => {
              event.stopPropagation();
              setIsSidebarCollapsed((prev) => !prev);
            }}
            className="rounded-full border border-slate-200 bg-white p-2 text-slate-600 shadow-sm hover:bg-slate-50"
          >
            <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="4" y1="7" x2="20" y2="7" />
              <line x1="4" y1="12" x2="20" y2="12" />
              <line x1="4" y1="17" x2="20" y2="17" />
            </svg>
          </button>
          {!isSidebarCollapsed && (
            <div className="space-y-1">
              <p className="text-xs uppercase tracking-[0.3em] text-slate-400">RagAgent</p>
              <h2 className="text-lg font-semibold text-slate-800">Workspace</h2>
            </div>
          )}
        </div>

        <button
          onClick={() => createConversation("New Chat")}
          className={`mt-4 flex items-center justify-center gap-2 rounded-xl bg-slate-900 text-white ${
            isSidebarCollapsed ? "p-2" : "px-4 py-3 text-sm font-semibold"
          }`}
        >
          <span className="text-lg leading-none">+</span>
          {!isSidebarCollapsed && <span>New chat</span>}
        </button>

        <div className={`mt-6 flex-1 ${isSidebarCollapsed ? "overflow-hidden" : "overflow-y-auto"} pr-1`}>
          {!isSidebarCollapsed && (
            <p className="text-xs uppercase tracking-[0.3em] text-slate-400">Recent Chat</p>
          )}
          {!isSidebarCollapsed && (
            <div className="mt-3 space-y-2">
              {conversations.length === 0 && (
                <div className="rounded-xl border border-slate-200 bg-white px-3 py-4 text-xs text-slate-500">
                  No conversations yet.
                </div>
              )}
              {conversations.map((conv) => (
                <div key={conv.id} className="group relative">
                  <button
                    onClick={() => {
                      setActiveConversation(conv.id);
                      setActiveMenuId(null);
                      setShowUserMenu(false);
                    }}
                    className={`flex w-full flex-col gap-1 rounded-2xl border px-3 py-3 text-left text-sm transition ${
                      activeConversation === conv.id
                        ? "border-slate-300 bg-slate-100"
                        : "border-slate-200 bg-white hover:border-slate-300"
                    }`}
                  >
                    <span className="line-clamp-1 font-medium text-slate-800">{conv.title || "Untitled"}</span>
                    <span className="text-xs text-slate-500">{conv.createdTime?.slice(0, 10)}</span>
                  </button>
                  <button
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation();
                      setActiveMenuId((prev) => (prev === conv.id ? null : conv.id));
                    }}
                    className="absolute right-2 top-3 rounded-full p-1 text-slate-500 opacity-0 transition hover:bg-slate-100 group-hover:opacity-100"
                  >
                    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="currentColor">
                      <circle cx="6" cy="12" r="2" />
                      <circle cx="12" cy="12" r="2" />
                      <circle cx="18" cy="12" r="2" />
                    </svg>
                  </button>
                  {activeMenuId === conv.id && (
                    <div
                      className="absolute right-2 top-10 z-20 w-32 rounded-xl border border-slate-200 bg-white shadow-lg"
                      onClick={(event) => event.stopPropagation()}
                    >
                      <button
                        type="button"
                        onClick={() => handleRenameConversation(conv)}
                        className="w-full px-3 py-2 text-left text-xs text-slate-700 hover:bg-slate-50"
                      >
                        Rename
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDeleteConversation(conv)}
                        className="w-full px-3 py-2 text-left text-xs text-rose-600 hover:bg-rose-50"
                      >
                        Delete
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        <div className={`mt-4 space-y-3 ${isSidebarCollapsed ? "flex flex-col items-center" : ""}`}>
          <div
            className={`rounded-xl border border-slate-200 bg-white text-xs text-slate-500 ${
              isSidebarCollapsed ? "px-2 py-2 text-center" : "px-3 py-2"
            }`}
          >
            {isSidebarCollapsed ? (
              isLive && <span className="inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500" />
            ) : (
              <div className="flex items-center gap-2">
                {isLive && <span className="inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500" />}
                <span>
                  Status: <span className="text-slate-800">{pendingLabel}</span>
                </span>
              </div>
            )}
          </div>
          <div className="relative">
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                setShowUserMenu((prev) => !prev);
              }}
              className={
                isSidebarCollapsed
                  ? "mx-auto flex h-11 w-11 items-center justify-center rounded-full border border-slate-200 bg-white hover:bg-slate-50"
                  : "flex w-full items-center gap-3 rounded-xl border border-slate-200 bg-white px-3 py-2 text-left hover:bg-slate-50"
              }
            >
              <div className="h-9 w-9 rounded-full border border-slate-200 bg-slate-100" />
              {!isSidebarCollapsed && (
                <div>
                  <p className="text-sm font-medium text-slate-800">{user?.username || "user"}</p>
                  <p className="text-xs text-slate-500">Account</p>
                </div>
              )}
            </button>
            {showUserMenu && !isSidebarCollapsed && (
              <div
                className="absolute bottom-12 left-0 z-30 w-44 rounded-xl border border-slate-200 bg-white shadow-lg"
                onClick={(event) => event.stopPropagation()}
              >
                <button
                  type="button"
                  className="w-full px-3 py-2 text-left text-xs text-slate-700 hover:bg-slate-50"
                >
                  Setting
                </button>
                <button
                  type="button"
                  className="w-full px-3 py-2 text-left text-xs text-slate-700 hover:bg-slate-50"
                >
                  Help
                </button>
                <button
                  type="button"
                  onClick={handleLogout}
                  className="w-full px-3 py-2 text-left text-xs text-rose-600 hover:bg-rose-50"
                >
                  Logout
                </button>
              </div>
            )}
          </div>
        </div>
      </aside>

      <main className="glass-strong flex h-full flex-1 flex-col overflow-hidden rounded-3xl">
        <header className="grid grid-cols-[1fr_auto_1fr] items-center border-b border-slate-200 bg-white/70 px-6 py-4">
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <span className="rounded-full border border-slate-300 px-3 py-1 text-slate-700">{pendingLabel}</span>
          </div>
          <div className="text-center">
            <h3 className="text-base font-semibold text-slate-800">
              {activeConv ? activeConv.title || "Untitled" : "Welcome"}
            </h3>
          </div>
          <div className="flex items-center justify-end text-xs text-slate-400">
            API {API_BASE.replace("http://", "").replace("https://", "")}
          </div>
        </header>

        <section className="flex-1 overflow-y-auto px-6 py-6">
          {messages.length === 0 ? (
            <EmptyState onStart={() => createConversation("New Chat")} />
          ) : (
            <div className="space-y-6">
              {messages.map((msg) => (
                <ChatBubble
                  key={`${msg.role}-${msg.id}`}
                  role={msg.role}
                  content={msg.content}
                  streaming={msg.streaming}
                  time={
                    msg.ts
                      ? new Date(msg.ts).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
                      : ""
                  }
                />
              ))}
              <div ref={scrollRef} />
            </div>
          )}
        </section>

        <footer className="border-t border-slate-200 bg-white/70 px-6 py-4">
          <div className="flex flex-col gap-3 md:flex-row md:items-center">
            <textarea
              className="min-h-[54px] flex-1 resize-none rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 outline-none focus:border-slate-500"
              placeholder="Type your message..."
              value={composer}
              onChange={(event) => setComposer(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  sendMessage();
                }
              }}
            />
            <button
              onClick={sendMessage}
              className="rounded-2xl bg-slate-900 px-5 py-3 text-sm font-semibold text-white"
            >
              Send
            </button>
          </div>
          <p className="mt-3 text-xs text-slate-400">
            Press Enter to send, Shift + Enter for a new line.
          </p>
        </footer>
      </main>
    </div>
  );
}
