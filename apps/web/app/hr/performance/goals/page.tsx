import GoalsClient from "./goals-client";

export const dynamic = "force-dynamic";
export const revalidate = 0;

export default function GoalsPage() {
  return <GoalsClient />;
}
