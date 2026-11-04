USE `mark`;

INSERT INTO `assignment`
  (`id`, `name`, `introduction`, `instructions`,
   `gradingCriteriaOverview`, `timeEstimateMinutes`, `passingGrade`, `published`)
VALUES
  (1,
   '初一数学·有理数基础练习',
   '通过选择题和判断题复习有理数加法、绝对值与一元一次方程。',
   '请完成 3 道基础题，每题只选择一个答案。',
   '每题 1 分，达到 60% 即为通过。对错由 Java 后端根据标准答案计算，AI 只生成讲评草稿。',
   10, 60, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `introduction` = VALUES(`introduction`),
  `instructions` = VALUES(`instructions`),
  `gradingCriteriaOverview` = VALUES(`gradingCriteriaOverview`);

INSERT INTO `question`
  (`id`, `assignmentId`, `type`, `question`, `authorComment`,
   `totalPoints`, `choices`, `answer`, `randomizedChoices`, `isDeleted`)
VALUES
  (1, 1, 'SINGLE_CORRECT', '计算：(-2) + 5 = ?',
   '先确定符号，再计算绝对值。', 1,
   JSON_ARRAY(
     JSON_OBJECT('id', 1, 'choice', '2', 'isCorrect', FALSE, 'points', 0, 'feedback', ''),
     JSON_OBJECT('id', 2, 'choice', '3', 'isCorrect', TRUE, 'points', 1, 'feedback', '答案正确'),
     JSON_OBJECT('id', 3, 'choice', '-3', 'isCorrect', FALSE, 'points', 0, 'feedback', '检查符号'),
     JSON_OBJECT('id', 4, 'choice', '-7', 'isCorrect', FALSE, 'points', 0, 'feedback', '检查加法')
   ), NULL, FALSE, FALSE),
  (2, 1, 'TRUE_FALSE', '绝对值等于 3 的数只有 3。', '', 1,
   JSON_ARRAY(
     JSON_OBJECT('id', 1, 'choice', '正确', 'isCorrect', FALSE, 'points', 0, 'feedback', '还有 -3'),
     JSON_OBJECT('id', 2, 'choice', '错误', 'isCorrect', TRUE, 'points', 1, 'feedback', '绝对值等于 3 的数是 3 和 -3')
   ), NULL, FALSE, FALSE),
  (3, 1, 'SINGLE_CORRECT', '方程 3x = 12 的解是 x = ?',
   '方程两边同时除以 3。', 1,
   JSON_ARRAY(
     JSON_OBJECT('id', 1, 'choice', '2', 'isCorrect', FALSE, 'points', 0, 'feedback', '再计算一次'),
     JSON_OBJECT('id', 2, 'choice', '3', 'isCorrect', FALSE, 'points', 0, 'feedback', '再计算一次'),
     JSON_OBJECT('id', 3, 'choice', '4', 'isCorrect', TRUE, 'points', 1, 'feedback', '答案正确'),
     JSON_OBJECT('id', 4, 'choice', '6', 'isCorrect', FALSE, 'points', 0, 'feedback', '再计算一次')
   ), NULL, FALSE, FALSE)
ON DUPLICATE KEY UPDATE
  `question` = VALUES(`question`),
  `choices` = VALUES(`choices`),
  `totalPoints` = VALUES(`totalPoints`),
  `isDeleted` = FALSE;

INSERT INTO `class`
  (`id`, `name`, `classCode`, `subject`, `term`, `gradeLevel`)
VALUES
  (1, '初一（1）班', 'MATH-701', '数学', '2026-秋', '七年级')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `subject` = VALUES(`subject`),
  `term` = VALUES(`term`);

INSERT INTO `assignmentclass` (`assignmentId`, `classId`)
VALUES (1, 1)
ON DUPLICATE KEY UPDATE `assignmentId` = VALUES(`assignmentId`);

INSERT INTO `knowledgepoint` (`id`, `name`, `code`, `subject`, `level`)
VALUES
  (1, '有理数加法', 'MATH-701-01', '数学', 1),
  (2, '绝对值', 'MATH-701-02', '数学', 1),
  (3, '一元一次方程', 'MATH-701-03', '数学', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `subject` = VALUES(`subject`);

INSERT INTO `questionknowledgepoint`
  (`questionId`, `knowledgePointId`, `weight`, `isPrimary`)
VALUES
  (1, 1, 1, TRUE),
  (2, 2, 1, TRUE),
  (3, 3, 1, TRUE)
ON DUPLICATE KEY UPDATE `weight` = VALUES(`weight`);
