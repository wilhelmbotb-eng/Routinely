# Routinely 2.0 — Product Roadmap

Product direction: luxury black-and-white Android productivity companion. Offline-first core, optional secure AI.

## Product principles
- Premium, calm, readable interface; no clutter or noisy gamification.
- Routine creation, completion and history work without an internet connection.
- Ask permission before sensitive integrations or actions.
- AI is optional and must never block core functionality.
- No API secrets embedded in the Android application.

## Milestone 1 — Reliable foundation
- [x] Luxury monochrome dashboard foundation
- [x] Add and complete routines
- [x] Local persistence for routines
- [x] Daily completion streak foundation
- [x] Starter achievement badges
- [ ] Routine edit, delete, reorder and categories
- [ ] Day rollover and reliable per-day completion history
- [ ] Unit and UI tests

## Milestone 2 — Planning and habit tracking
- [ ] Daily, weekly and custom repeat schedules
- [ ] Habit goals and flexible completion rules
- [ ] Calendar and agenda views
- [ ] Weekly/monthly statistics and completion heatmap
- [ ] Goals, milestones and progress summaries
- [ ] Export and restore user data

## Milestone 3 — Routinely AI
- [x] AI assistant entry point and prompt ideas in the app
- [ ] Secure backend API and provider abstraction
- [ ] User-controlled AI settings and privacy disclosure
- [ ] Natural-language routine generation
- [ ] Day/week planning and schedule balancing
- [ ] Suggestions based on user-approved routine data
- [ ] Confirm-before-apply workflow for AI changes
- [ ] Rate limits, error handling and cost controls

## Milestone 4 — Alarms and reminders
- [ ] Alarm profiles and recurring schedules
- [ ] Exact alarm permission flow where required by Android
- [ ] Notification permission onboarding
- [ ] Snooze and dismiss actions
- [ ] Bedtime and wind-down reminders
- [ ] Reboot rescheduling and battery-optimisation guidance
- [ ] Custom sounds and vibration options

## Milestone 5 — Android integration and personalisation
- [ ] Home-screen widgets (Jetpack Glance)
- [ ] Quick Settings tile and notification actions
- [ ] Calendar integration with explicit permission
- [ ] AMOLED black, light and monochrome accent options
- [ ] Typography, haptics and accessible motion settings
- [ ] Backup/restore and optional sync

## Milestone 6 — Focus and life tools
- [ ] Pomodoro/focus timer
- [ ] Focus sessions linked to routines
- [ ] Journal and notes
- [ ] Goal planner
- [ ] Screen-time insights only with explicit user consent

## Milestone 7 — Release quality
- [ ] Automated unit/UI tests and CI checks
- [ ] Accessibility and small-screen testing
- [ ] Crash and performance review
- [ ] Privacy policy and data export/delete flows
- [ ] App signing and release APK/AAB
- [ ] Play Store readiness review

## Current implementation note
The current AI screen is a UI entry point only. It does not pretend to generate AI responses until a secure backend is configured. Alarm, widget, calendar and cloud-sync features are roadmap items, not yet implemented.
