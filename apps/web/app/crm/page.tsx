import { CrmAdvancedView } from "./crm-advanced-view";
import { CrmWorkspaceV2 } from "./crm-workspace-v2";

export default function CrmPage() {
  return (
    <>
      <CrmWorkspaceV2 />
      <CrmAdvancedView />
    </>
  );
}
