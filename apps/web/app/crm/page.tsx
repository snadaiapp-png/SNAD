import { CrmWorkspaceV2 } from "./crm-workspace-v2";
import { CrmAdvancedView } from "./crm-advanced-view";

export default function CrmPage() {
  return (
    <>
      <CrmWorkspaceV2 />
      <CrmAdvancedView />
    </>
  );
}
