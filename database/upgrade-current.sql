USE `mark`;

ALTER TABLE `math_ai_student_submission`
  ADD COLUMN `studentKey` VARCHAR(96) NOT NULL DEFAULT 'demo-student'
  AFTER `assignmentId`;

ALTER TABLE `math_ai_student_submission`
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (`studentKey`, `assignmentId`),
  ADD KEY `submission_assignment_idx` (`assignmentId`);

ALTER TABLE `math_ai_student_correction`
  ADD COLUMN `studentKey` VARCHAR(96) NOT NULL DEFAULT 'demo-student'
  AFTER `id`,
  ADD KEY `correction_student_assignment_idx` (`studentKey`, `assignmentId`);
