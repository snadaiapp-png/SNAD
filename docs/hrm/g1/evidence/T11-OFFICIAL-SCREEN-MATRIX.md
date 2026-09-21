# T11 Official Screen Matrix (per §14)

Source of truth: `docs/superpowers/specs/2026-09-07-hrm-g1-recruitment-onboarding-design.md` §14

| # | Screen | Route | Required Capabilities | Backend APIs | Status |
|---|---|---|---|---|---|
| 1 | Recruitment Dashboard | `/hr/recruitment` | HRM.RECRUITMENT.OPENING.VIEW + CANDIDATE.VIEW + APPLICATION.MANAGE | listOpenings + listCandidates + listApplications | DONE |
| 2 | Job Openings List | `/hr/recruitment/openings` | HRM.RECRUITMENT.OPENING.VIEW + .MANAGE + .PUBLISH | listOpenings + createOpening + submitOpening + approveOpening + rejectOpening + pauseOpening + closeOpening | DONE |
| 3 | Job Opening Detail | `/hr/recruitment/openings/[openingId]` | HRM.RECRUITMENT.OPENING.VIEW + .MANAGE + .PUBLISH | getOpening + submitOpening + approveOpening + rejectOpening + pauseOpening + closeOpening | **NOT_IMPLEMENTED** |
| 4 | Candidate Directory | `/hr/recruitment/candidates` | HRM.RECRUITMENT.CANDIDATE.VIEW + .MANAGE | listCandidates + archiveCandidate | DONE |
| 5 | Candidate Profile | `/hr/recruitment/candidates/[candidateId]` | HRM.RECRUITMENT.CANDIDATE.VIEW + .MANAGE | getCandidate + archiveCandidate | **NOT_IMPLEMENTED** |
| 6 | Applications Board (Pipeline) | `/hr/recruitment/applications` | HRM.RECRUITMENT.APPLICATION.MANAGE + .ADVANCE + .REJECT | listApplications + getApplicationPipeline + advanceApplication + rejectApplication | **NOT_IMPLEMENTED** |
| 7 | Application Detail | `/hr/recruitment/applications/[applicationId]` | HRM.RECRUITMENT.APPLICATION.MANAGE + .ADVANCE + .REJECT + .WITHDRAW | getApplication + advanceApplication + rejectApplication + withdrawApplication + createInterview | **NOT_IMPLEMENTED** |
| 8 | Interview Scheduling | `/hr/recruitment/interviews/schedule` | HRM.RECRUITMENT.INTERVIEW.SCHEDULE + .MANAGE | listApplications + scheduleInterview + getInterview | **NOT_IMPLEMENTED** |
| 9 | Interview Feedback | `/hr/recruitment/interviews/[interviewId]/feedback` | HRM.RECRUITMENT.INTERVIEW.MANAGE | getInterview + getInterviewFeedback + putInterviewFeedback | **NOT_IMPLEMENTED** |
| 10 | Offer Editor | `/hr/recruitment/offers/[offerId]/edit` | HRM.RECRUITMENT.OFFER.MANAGE + .EXTEND | createOffer + extendOffer | **NOT_IMPLEMENTED** |
| 11 | Offer Approval | `/hr/recruitment/offers/[offerId]/approve` | HRM.RECRUITMENT.OFFER.ACCEPT + .DECLINE | extendOffer + acceptOffer + declineOffer + withdrawOffer | **NOT_IMPLEMENTED** |
| 12 | Hire Conversion | `/hr/recruitment/offers/[offerId]/convert` | HRM.RECRUITMENT.HIRE.CONVERT | convertOfferToHire | **NOT_IMPLEMENTED** |
| 13 | Onboarding Dashboard | `/hr/onboarding` | HRM.ONBOARDING.PLAN.VIEW + .MANAGE | listPlans | DONE |
| 14 | Onboarding Plan Detail | `/hr/onboarding/plans/[planId]` | HRM.ONBOARDING.PLAN.VIEW + .MANAGE + TASK.COMPLETE + .WAIVE | getPlan + completeTask + waiveTask + cancelPlan | DONE |
| 15 | Onboarding Tasks (my tasks) | `/hr/onboarding/tasks` | HRM.ONBOARDING.TASK.COMPLETE + .WAIVE | listPlans (filtered by assignee) + completeTask + waiveTask | **NOT_IMPLEMENTED** |

**Coverage as of checkpoint `64a27b45`:** 5/15 = 33%  
**Target:** 15/15 = 100%

Quality requirements per screen (production-grade, not scaffold):
- Real API integration via `hrmRecruitmentApi` / `hrmOnboardingApi`
- Loading state (HrLoading)
- Empty state (HrEmptyState with CTA)
- Error state (HrErrorState)
- Permission-denied state (no capability → no control rendered)
- Form validation (where applicable)
- Mutation success/failure feedback
- Arabic + English i18n keys (no hard-coded strings)
- RTL via logical CSS (no left/right physical)
- WCAG a11y (semantic HTML, ARIA, keyboard paths)
- SDS tokens only (no hardcoded hex colors)
- Responsive desktop + mobile
- Page test (render + capability scoping + error state minimum)
