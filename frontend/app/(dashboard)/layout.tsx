"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { getToken, clearToken } from "@/lib/api";
import { clearCurrentUser } from "@/lib/auth";
import { isTokenValid } from "@/lib/session";
import { IseStatusProvider } from "@/lib/IseStatusContext";
import { Sidebar } from "@/components/layout/Sidebar";
import { Topbar } from "@/components/layout/Topbar";
import { IseBanner } from "@/components/ui/IseBanner";
import { TabStatus } from "@/components/layout/TabStatus";
import { LoadingState } from "@/components/ui/AsyncState";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [ready, setReady] = useState(false);
  const [navigationOpen, setNavigationOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const token = getToken();
    if (!isTokenValid(token)) {
      clearToken();
      clearCurrentUser();
      router.replace("/login");
      return;
    }
    setReady(true);
  }, [router]);

  useEffect(() => {
    if (!navigationOpen) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setNavigationOpen(false);
        menuButtonRef.current?.focus();
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [navigationOpen]);

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <LoadingState label="Checking your session…" />
      </div>
    );
  }

  return (
    <IseStatusProvider>
      <TabStatus />
      <a href="#main-content" className="sr-only z-[100] rounded bg-panel p-3 text-ink focus:not-sr-only focus:fixed focus:left-3 focus:top-3">
        Skip to main content
      </a>
      <div className="flex min-h-screen md:h-screen md:overflow-hidden">
        <Sidebar mobileOpen={navigationOpen} onClose={() => setNavigationOpen(false)} />
        <div className="flex min-w-0 flex-1 flex-col">
          <Topbar
            navigationOpen={navigationOpen}
            onToggleNavigation={() => setNavigationOpen((open) => !open)}
            menuButtonRef={menuButtonRef}
          />
          <IseBanner />
          <main id="main-content" tabIndex={-1} className="min-w-0 flex-1 overflow-x-hidden p-4 sm:p-6 lg:overflow-y-auto lg:p-8">{children}</main>
        </div>
      </div>
    </IseStatusProvider>
  );
}
