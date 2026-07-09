# Simple TCP proxy that forwards IPv4 connections to the Supabase IPv6 database.
# This allows Render's IPv4-only free tier to connect to Supabase's IPv6-only database.
#
# The proxy listens on port 5432 (IPv4) and forwards to the Supabase database host.

FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends socat && rm -rf /var/lib/apt/lists/*

EXPOSE 5432

# Forward IPv4:5432 to Supabase IPv6 database
# The IPv6 address is resolved at connection time
CMD ["socat", "TCP-LISTEN:5432,fork,reuseaddr", "TCP6:db.eyoupksefusnslvdnflt.supabase.co:5432"]
