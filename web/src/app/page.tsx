"use client";

import { motion } from "framer-motion";
import { ArrowRight, Building2, KeyRound, ShieldCheck, Users } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

const container = {
  hidden: {},
  show: { transition: { staggerChildren: 0.08 } },
};

const item = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: "easeOut" as const } },
};

const FEATURES = [
  {
    icon: Building2,
    title: "Platform → organisation hierarchy",
    description:
      "One platform owns many organisations. Manage them all from a single console with per-org security policies.",
    color: "from-blue-500/10 to-indigo-500/10 text-blue-600 dark:text-blue-400",
  },
  {
    icon: Users,
    title: "Organisation RBAC",
    description:
      "Organisation users get roles, roles group fixed permissions. Create roles with fine-grained user and user-field access.",
    color: "from-emerald-500/10 to-teal-500/10 text-emerald-600 dark:text-emerald-400",
  },
  {
    icon: KeyRound,
    title: "Org-owned signing keys",
    description:
      "Every organisation signs its own tokens with its own RSA key. Rotate keys on demand — old tokens keep verifying until expiry.",
    color: "from-amber-500/10 to-orange-500/10 text-amber-600 dark:text-amber-400",
  },
  {
    icon: ShieldCheck,
    title: "Security by default",
    description:
      "Per-org password policies, session limits, rotating refresh tokens with theft detection, rate limiting and a full audit trail.",
    color: "from-violet-500/10 to-purple-500/10 text-violet-600 dark:text-violet-400",
  },
];

export default function HomePage() {
  return (
    <div className="relative min-h-dvh overflow-hidden bg-background">
      {/* Animated grid backdrop */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-grid opacity-40"
      />
      {/* Gradient orbs */}
      <div
        aria-hidden
        className="pointer-events-none absolute -top-40 left-1/2 h-[500px] w-[800px] -translate-x-1/2 rounded-full bg-[radial-gradient(ellipse_at_center,color-mix(in_oklab,var(--primary)_18%,transparent),transparent_70%)] blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -bottom-40 -right-40 h-[400px] w-[400px] rounded-full bg-[radial-gradient(ellipse_at_center,color-mix(in_oklab,var(--chart-2)_12%,transparent),transparent_70%)] blur-3xl"
      />

      <header className="relative mx-auto flex w-full max-w-6xl items-center justify-between px-4 py-6 sm:px-6">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-lg shadow-primary/25">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <span className="text-lg font-semibold tracking-tight">Nexxauth</span>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="ghost" asChild>
            <Link href="/login">Sign in</Link>
          </Button>
          <Button asChild className="shadow-md shadow-primary/10">
            <Link href="/register">Create platform</Link>
          </Button>
        </div>
      </header>

      <motion.main
        variants={container}
        initial="hidden"
        animate="show"
        className="relative mx-auto flex w-full max-w-6xl flex-col items-center px-4 pb-24 pt-16 sm:px-6 sm:pt-24"
      >
        <motion.div
          variants={item}
          className="mb-8 flex items-center gap-2 rounded-full border bg-secondary/50 px-4 py-1.5 text-xs font-medium text-secondary-foreground backdrop-blur-sm"
        >
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-primary" />
          Multi-tenant authentication for the Nexxserve platform
        </motion.div>

        <motion.h1
          variants={item}
          className="max-w-3xl text-center text-4xl font-semibold leading-[1.1] tracking-tight sm:text-6xl"
        >
          Manage platforms, organisations and{" "}
          <span className="text-gradient">security policies</span> in one place
        </motion.h1>

        <motion.p
          variants={item}
          className="mt-6 max-w-2xl text-center text-lg leading-relaxed text-muted-foreground"
        >
          Nexxauth Console is the admin home for your platform — create organisations,
          manage their users and roles, tune password policies and session settings,
          and rotate signing keys, all with a modern, fast interface.
        </motion.p>

        <motion.div variants={item} className="mt-10 flex flex-col items-center gap-3 sm:flex-row">
          <Button size="lg" asChild className="w-full shadow-lg shadow-primary/20 sm:w-auto">
            <Link href="/register">
              Get started — it&apos;s free
              <ArrowRight className="transition-transform group-hover:translate-x-0.5" />
            </Link>
          </Button>
          <Button size="lg" variant="outline" asChild className="w-full sm:w-auto">
            <Link href="/login">Sign in to your platform</Link>
          </Button>
        </motion.div>

        {/* Feature cards */}
        <motion.div variants={item} className="mt-24 grid w-full gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((feature, i) => {
            const Icon = feature.icon;
            return (
              <motion.div
                key={feature.title}
                variants={item}
                whileHover={{ y: -4, transition: { duration: 0.2 } }}
              >
                <Card className="h-full transition-shadow hover:shadow-xl hover:shadow-primary/5">
                  <CardContent className="p-5">
                    <div
                      className={`mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br ${feature.color}`}
                    >
                      <Icon className="h-5 w-5" />
                    </div>
                    <h3 className="text-sm font-semibold leading-snug">{feature.title}</h3>
                    <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                      {feature.description}
                    </p>
                  </CardContent>
                </Card>
              </motion.div>
            );
          })}
        </motion.div>

        {/* Trust indicators */}
        <motion.div
          variants={item}
          className="mt-20 flex flex-wrap items-center justify-center gap-6 text-sm text-muted-foreground"
        >
          <span className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4 text-primary" />
            RS256 JWT signing
          </span>
          <span className="hidden h-4 w-px bg-border sm:block" />
          <span className="flex items-center gap-2">
            <KeyRound className="h-4 w-4 text-primary" />
            Rotating refresh tokens
          </span>
          <span className="hidden h-4 w-px bg-border sm:block" />
          <span className="flex items-center gap-2">
            <Users className="h-4 w-4 text-primary" />
            Multi-tenant RBAC
          </span>
        </motion.div>
      </motion.main>

      {/* Footer */}
      <footer className="relative border-t bg-background/80 backdrop-blur-sm">
        <div className="mx-auto flex w-full max-w-6xl flex-col items-center justify-between gap-4 px-4 py-8 sm:flex-row sm:px-6">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <div className="flex h-6 w-6 items-center justify-center rounded-md bg-primary/10 text-primary">
              <ShieldCheck className="h-3.5 w-3.5" />
            </div>
            Nexxauth
          </div>
          <p className="text-xs text-muted-foreground">
            Multi-tenant authentication platform built with Nexxserve.
          </p>
        </div>
      </footer>
    </div>
  );
}
