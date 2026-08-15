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
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { duration: 0.45, ease: "easeOut" as const } },
};

const FEATURES = [
  {
    icon: Building2,
    title: "Platform → organisation hierarchy",
    description:
      "One platform owns many organisations. Manage them all from a single console with per-org security policies.",
  },
  {
    icon: Users,
    title: "Organisation RBAC",
    description:
      "Organisation users get roles, roles group fixed permissions. Create roles with fine-grained user and user-field access.",
  },
  {
    icon: KeyRound,
    title: "Org-owned signing keys",
    description:
      "Every organisation signs its own tokens with its own RSA key. Rotate keys on demand — old tokens keep verifying until expiry.",
  },
  {
    icon: ShieldCheck,
    title: "Security by default",
    description:
      "Per-org password policies, session limits, rotating refresh tokens with theft detection, rate limiting and a full audit trail.",
  },
];

export default function HomePage() {
  return (
    <div className="relative min-h-dvh overflow-hidden bg-background">
      {/* Gradient backdrop */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(50%_40%_at_50%_-5%,color-mix(in_oklab,var(--primary)_22%,transparent),transparent)]"
      />

      <header className="relative mx-auto flex w-full max-w-6xl items-center justify-between px-4 py-6 sm:px-6">
        <div className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <span className="text-lg font-semibold tracking-tight">Nexxauth</span>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="ghost" asChild>
            <Link href="/login">Sign in</Link>
          </Button>
          <Button asChild>
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
        <motion.div variants={item} className="mb-6 flex items-center gap-2 rounded-full border bg-secondary/50 px-4 py-1.5 text-xs font-medium text-secondary-foreground">
          <span className="h-1.5 w-1.5 rounded-full bg-primary" />
          Multi-tenant authentication for the Nexxserve platform
        </motion.div>

        <motion.h1
          variants={item}
          className="max-w-3xl text-center text-4xl font-semibold leading-[1.1] tracking-tight sm:text-6xl"
        >
          Manage platforms, organisations and{" "}
          <span className="text-primary">security policies</span> in one place
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
          <Button size="lg" asChild className="w-full sm:w-auto">
            <Link href="/register">
              Get started — it&apos;s free
              <ArrowRight />
            </Link>
          </Button>
          <Button size="lg" variant="outline" asChild className="w-full sm:w-auto">
            <Link href="/login">Sign in to your platform</Link>
          </Button>
        </motion.div>

        <motion.div variants={item} className="mt-20 grid w-full gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((feature) => {
            const Icon = feature.icon;
            return (
              <Card key={feature.title} className="transition-shadow hover:shadow-lg">
                <CardContent className="p-5">
                  <div className="mb-3 flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <Icon className="h-4.5 w-4.5" />
                  </div>
                  <h3 className="text-sm font-semibold leading-snug">{feature.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {feature.description}
                  </p>
                </CardContent>
              </Card>
            );
          })}
        </motion.div>
      </motion.main>
    </div>
  );
}
