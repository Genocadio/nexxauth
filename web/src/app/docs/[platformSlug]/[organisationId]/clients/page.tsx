"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import Link from "next/link";

export default function ClientsPage() {
  const docs = useDocs();
  const basePath = `/docs/${docs.organisation.platformSlug}/${docs.organisation.id}`;

  const clientTypes = [
    {
      type: "WEB",
      name: "WEB Client",
      description: "Browser-based applications (SPAs)",
      auth: "Never authenticated",
      capabilities: ["Login", "Register"],
      href: `${basePath}/clients/web`,
    },
    {
      type: "SERVER",
      name: "SERVER Client",
      description: "Backend services and APIs",
      auth: "Always authenticated (static token)",
      capabilities: ["Full API access", "User management", "Role management"],
      href: `${basePath}/clients/server`,
    },
    {
      type: "ANDROID",
      name: "ANDROID Client",
      description: "Android mobile applications",
      auth: "Configurable",
      capabilities: ["Login", "Register", "Full API (with auth)"],
      href: `${basePath}/clients/mobile`,
    },
    {
      type: "IOS",
      name: "IOS Client",
      description: "iOS mobile applications",
      auth: "Configurable",
      capabilities: ["Login", "Register", "Full API (with auth)"],
      href: `${basePath}/clients/mobile`,
    },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Client Types</h1>
        <p className="mt-2 text-muted-foreground">
          Understand the different client types and their capabilities.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        {clientTypes.map((client) => (
          <Link key={client.type} href={client.href}>
            <Card className="hover:border-primary/50 transition-colors h-full">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="text-lg">{client.name}</CardTitle>
                  <Badge variant="outline">{client.type}</Badge>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                <p className="text-sm text-muted-foreground">{client.description}</p>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">Authentication</p>
                  <p className="text-sm">{client.auth}</p>
                </div>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">Capabilities</p>
                  <div className="flex flex-wrap gap-1">
                    {client.capabilities.map((cap) => (
                      <Badge key={cap} variant="secondary" className="text-xs">
                        {cap}
                      </Badge>
                    ))}
                  </div>
                </div>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
