import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/auth/login",
        destination: "/",
      },
    ];
  },
};

export default nextConfig;
