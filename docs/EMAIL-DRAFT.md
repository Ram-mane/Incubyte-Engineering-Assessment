# Clarification email — draft

**To:** Incubyte hiring team (reply on the assessment thread)
**Subject:** Re: Salary Management Assessment — a few clarifying questions before I build

---

Hi team,

Thank you for the assessment — the framing around product thinking and engineering judgment is a
welcome change from the usual take-home.

Before I start building I would rather ask than assume. Most of these have a default I am comfortable
proceeding with, so please do not feel you need to answer every one — I have noted where I will go
ahead regardless, and I will document each assumption in the repo.

**On the product**

1. When you say *salary management*, should I read that as managing compensation data and reporting on
   it, or does it extend to payroll processing (tax, payslips, disbursement)? I am planning the former,
   and treating payroll as explicitly out of scope with the reasoning written down.
2. The problem statement mentions answering questions about how the org pays people. Are there specific
   questions the HR Manager needs answered? I have derived seven — payroll cost by dimension, salary
   distribution for a role, employees paid outside their band, trend over time, people overdue a
   review, merit-increase what-if, and point-in-time snapshots — but I would rather build the ones you
   actually have in mind.
3. Should the system hold full salary *history* (effective-dated changes, retroactive corrections),
   or is the current salary enough? I am building history, because most of the interesting questions
   above are historical, but it is the single largest design decision and I want to flag it.
4. Base pay only, or total rewards including bonus and equity? Planning base pay, with the model shaped
   so components can be added.
5. Are salary bands or ranges part of the domain? I am including them, as they are what let the system
   say something is *wrong* rather than just report a number.

**On the technical constraints**

6. The JD is for Java/Angular, but the assessment document lists "ReactJS or NextJS. AngularJS with
   Java". I am going with **Angular 19** as the closer match to the role — please tell me if React
   would actually be preferred.
7. Multiple countries: should salaries be held in local currency with a configurable reporting currency
   (my assumption, using dated FX rates), or normalised on entry?
8. Is a publicly reachable deployment on a free tier acceptable for the "deployed software"
   requirement, or do you have a preferred target?

**On scope**

9. Is an approval workflow for pay changes expected in scope? I am planning to defer it, record the
   approver on every change, and document how it would be layered on.
10. Is 10,000 employees steady-state? I am designing so the model does not break at much larger
    volumes and documenting the scale path with triggers, but building for the stated scale rather
    than speculatively.

I am aiming to submit within the next three to four days.

Best regards,
Ram Subhas Mane
