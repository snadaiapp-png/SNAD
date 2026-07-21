import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async redirects() {
    return [
      {
        source: "/crm",
        destination: "/crm/overview",
        permanent: false,
      },
    ];
  },
};

export default nextConfig;
