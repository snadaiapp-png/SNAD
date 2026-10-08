import PayrollClient from "./payroll-client";

export const dynamic = "force-dynamic";
export const revalidate = 0;

export default function PayrollPage() {
  return <PayrollClient />;
}
