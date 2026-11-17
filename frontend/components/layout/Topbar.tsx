"use client";

import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent, RefObject } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ChevronDown, ExternalLink, LogOut, Menu, Monitor, Search, X } from "lucide-react";
import { api, clearToken, type EndpointResponse } from "@/lib/api";
import { clearCurrentUser, getCurrentUser, initials, type CurrentUser } from "@/lib/auth";
import { ThemeToggle } from "./ThemeToggle";
import { ConnectionDot } from "../ui/ConnectionDot";

type SearchState = "idle" | "loading" | "ready" | "error";

export function Topbar({
  navigationOpen,
  onToggleNavigation,
  menuButtonRef,
}: {
  navigationOpen: boolean;
  onToggleNavigation: () => void;
  menuButtonRef: RefObject<HTMLButtonElement | null>;
}) {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [searchResults, setSearchResults] = useState<EndpointResponse[]>([]);
  const [searchFocused, setSearchFocused] = useState(false);
  const [searchState, setSearchState] = useState<SearchState>("idle");

  const menuRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setUser(getCurrentUser());
  }, []);

  useEffect(() => {
    function onClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setMenuOpen(false);
      if (searchRef.current && !searchRef.current.contains(event.target as Node)) setSearchFocused(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  useEffect(() => {
    const query = searchTerm.trim();
    if (!query) {
      setSearchResults([]);
      setSearchState("idle");
      return;
    }

    let cancelled = false;
    setSearchState("loading");
    const timer = window.setTimeout(() => {
      api
        .endpointsPage({ q: query, size: 5 })
        .then((result) => {
          if (cancelled) return;
          setSearchResults(result.items);
          setSearchState("ready");
        })
        .catch(() => {
          if (cancelled) return;
          setSearchResults([]);
          setSearchState("error");
        });
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [searchTerm]);

  function signOut() {
    clearToken();
    clearCurrentUser();
    router.push("/login");
  }

  function onSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") {
      setSearchFocused(false);
      searchInputRef.current?.blur();
    }
    if (event.key === "ArrowDown" && searchState === "ready" && searchResults.length > 0) {
      event.preventDefault();
      searchRef.current?.querySelector<HTMLAnchorElement>("a[href]")?.focus();
    }
  }

  const displayName = user?.username ?? "Account";

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center justify-between gap-3 border-b border-border bg-base/95 px-4 backdrop-blur sm:px-6">
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <button
          ref={menuButtonRef}
          type="button"
          onClick={onToggleNavigation}
          aria-label={navigationOpen ? "Close navigation" : "Open navigation"}
          aria-expanded={navigationOpen}
          aria-controls="primary-navigation"
          className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-md border border-border bg-panel text-ink md:hidden"
        >
          {navigationOpen ? <X size={18} aria-hidden="true" /> : <Menu size={18} aria-hidden="true" />}
        </button>

        <div className="relative min-w-0 w-full max-w-md" ref={searchRef}>
          <Search
            size={15}
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted"
          />
          <label className="sr-only" htmlFor="endpoint-search">
            Search endpoints by hostname, MAC address, or IP address
          </label>
          <input
            ref={searchInputRef}
            id="endpoint-search"
            type="search"
            role="combobox"
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            onFocus={() => setSearchFocused(true)}
            onKeyDown={onSearchKeyDown}
            aria-expanded={searchFocused && Boolean(searchTerm.trim())}
            aria-controls="endpoint-search-results"
            placeholder="Search endpoints by host, MAC or IP…"
            className="min-h-10 w-full rounded-md border border-border bg-panel py-2 pl-9 pr-3 text-sm text-ink placeholder:text-muted"
          />

          {searchFocused && searchTerm.trim() && (
            <div
              id="endpoint-search-results"
              className="absolute left-0 top-12 z-50 max-h-[min(70vh,28rem)] w-full overflow-y-auto rounded-lg border border-border bg-panel shadow-xl"
            >
              <div className="border-b border-border px-3 py-2 text-xs font-semibold text-muted">
                Endpoint search results
              </div>
              {searchState === "loading" && (
                <p className="p-3 text-sm text-muted" role="status">
                  Searching endpoints…
                </p>
              )}
              {searchState === "error" && (
                <p className="p-3 text-sm text-bad" role="alert">
                  Search failed. Try again.
                </p>
              )}
              {searchState === "ready" && searchResults.length === 0 && (
                <p className="p-3 text-sm text-muted" role="status">
                  No matching endpoints.
                </p>
              )}
              {searchState === "ready" && searchResults.length > 0 && (
                <ul aria-label="Matching endpoints" className="divide-y divide-border/60">
                  {searchResults.map((endpoint) => (
                    <li key={endpoint.id}>
                      <Link
                        href={`/endpoints/${endpoint.id}`}
                        onClick={() => {
                          setSearchFocused(false);
                          setSearchTerm("");
                        }}
                        className="flex min-h-14 items-center justify-between gap-3 px-3 py-2 hover:bg-ink/[0.04]"
                      >
                        <span className="flex min-w-0 items-start gap-2">
                          <Monitor size={15} aria-hidden="true" className="mt-0.5 flex-shrink-0 text-accent" />
                          <span className="min-w-0">
                            <span className="block truncate text-sm font-semibold text-ink">
                              {endpoint.hostname ?? endpoint.macAddress}
                            </span>
                            <span className="block break-all font-mono text-xs text-muted">
                              {endpoint.ipAddress ?? "No IP"} · {endpoint.macAddress}
                            </span>
                          </span>
                        </span>
                        <span className="flex flex-shrink-0 items-center gap-2">
                          <ConnectionDot connected={endpoint.connected} />
                          <ExternalLink size={13} aria-hidden="true" className="text-muted" />
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      </div>

      <div className="flex flex-shrink-0 items-center gap-2 sm:gap-3">
        <ThemeToggle />
        <div className="relative" ref={menuRef}>
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
            aria-haspopup="menu"
            className="flex min-h-10 items-center gap-2 rounded-md border border-border bg-panel px-2 text-sm text-ink hover:border-accent/40"
          >
            <span
              aria-hidden="true"
              className="flex h-7 w-7 items-center justify-center rounded-md bg-accent/15 text-xs font-bold text-accent"
            >
              {initials(displayName)}
            </span>
            <span className="hidden max-w-36 truncate text-left font-medium sm:block">{displayName}</span>
            <ChevronDown size={14} aria-hidden="true" className="text-muted" />
          </button>

          {menuOpen && (
            <div
              role="menu"
              aria-label="Account menu"
              className="absolute right-0 top-12 z-50 w-48 overflow-hidden rounded-lg border border-border bg-panel shadow-xl"
            >
              {user?.role && (
                <p className="border-b border-border px-3 py-2 text-xs text-muted">Role: {user.role}</p>
              )}
              <button
                type="button"
                role="menuitem"
                onClick={signOut}
                className="flex min-h-11 w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-bad hover:bg-bad/10"
              >
                <LogOut size={15} aria-hidden="true" />
                Sign out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
