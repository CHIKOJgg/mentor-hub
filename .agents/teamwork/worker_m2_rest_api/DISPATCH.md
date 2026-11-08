## 2026-11-08T14:47:40Z
You are a teamwork_worker. Your Working Directory is:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m2_rest_api\

MANDATORY FIRST STEP:
Read the authoritative user request at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

READ YOUR INPUT SPECIFICATIONS:
- Read c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_backend_survey\handoff.md (Part I: Complete REST API Controllers & Endpoints, JSON Schemas, Edge Cases)
- Read c:\Users\Honor\Desktop\miranda\frontend\src\types.ts and frontend\src\mockData.ts (exact camelCase field names expected by UI)
- Read c:\Users\Honor\Desktop\miranda\.agents\teamwork\orchestrator\PROJECT.md

YOUR MILESTONE SCOPE (M2: REST API Controllers & Business Logic Services):
You have exclusive write ownership over:
- src/main/java/com/mentorhub/dto/**
- src/main/java/com/mentorhub/service/**
- src/main/java/com/mentorhub/controller/**
- src/test/java/com/mentorhub/controller/**
- src/test/java/com/mentorhub/service/**

IMPLEMENTATION INSTRUCTIONS:
1. Create DTO records/classes in com.mentorhub.dto:
   - DailyReportResponse, ContentDraftDto, ReelDto, ReflectionSummaryDto, ApproveReportRequest, RejectReportRequest, ReportActionResponse
   - StudentDisciplineResponse, DayInfoResponse, CreateStudentRequest, UpdateStudentRequest, TogglePauseRequest, TogglePauseResponse
   - AnalyticsSummaryResponse, StoryCaseResponse
   - TaskRuleResponse, UpdateTaskRuleRequest
   - SystemStatusResponse, ZoomTrackerResponse, ScheduleZoomRequest
2. Implement Business Logic Services in com.mentorhub.service:
   - ReportService: review queue for /api/inbox (combining reports, content drafts, reels, reflection summary), approveReport (updating status to APPROVED, saving replyText), rejectReport (status REJECTED, saving reason).
   - StudentService: getStudents with weekly discipline calendar matrix (Mon-Sun), calculate disciplineRate %, generate tooltips (missedDetails), handle togglePause (/api/students/{id}/toggle-pause) with streak freeze and pauseReason, enforce Sunday weekend rule (status: 'WEEKEND', tooltip: 'Воскресенье — общий выходной потока ☕', excluded from discipline penalties), and student CRUD.
   - AnalyticsService: calculate the 10 cross-cutting metrics (/api/analytics/summary: totalEarned, revenueGrowthFactor, totalReels, totalCarousels, totalStorytells, totalMailings, totalNewClients, currentAvgTicket, initialAvgTicket, avgTicketGrowthFactor, averageDisciplineRate, activeBurnoutAlertsCount), and generate Instagram Stories case (/api/analytics/case/{studentId}) with Point A->B multiplier and division-by-zero protection (e.g., initialIncome=0 -> "x1.0").
   - TaskRuleService: /api/tasks/rules list and update.
   - SystemService: /api/system/status health diagnostics, /api/system/zoom-tracker get and schedule Zoom meeting.
3. Implement REST Controllers in com.mentorhub.controller:
   - InboxController (@RequestMapping("/api/inbox"))
   - StudentController (@RequestMapping("/api/students"))
   - AnalyticsController (@RequestMapping("/api/analytics"))
   - TaskRulesController (@RequestMapping("/api/tasks"))
   - SystemController (@RequestMapping("/api/system"))
   Ensure standard camelCase JSON serialization matching frontend contracts.
4. Write thorough unit and slice tests in src/test/java/com/mentorhub/controller/ and src/test/java/com/mentorhub/service/:
   - Test all 5 controllers with MockMvc (both with valid admin token and verifying response JSON payloads).
   - Test sick/pause streak freeze and Sunday weekend calculation.
   - Test division-by-zero protection in story case generator.
   - Test 10 cross-cutting analytics metrics calculation.
5. Verification:
   - Run `mvn clean test` and verify that ALL backend tests pass with 100% success rate.
   - Run `node scripts/test-runner.mjs` and verify that all 73 frontend automated tests continue to pass 100%.

Produce a detailed handoff report in:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m2_rest_api\handoff.md
Include build commands and execution logs in your handoff report.
Update progress.md in your directory as you work.
When done, message your caller.
