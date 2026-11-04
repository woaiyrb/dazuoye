-- 多端互通数据库扩展（MySQL 8.0）
-- Node / Java / Vue 共用账号、作业、作答、讲评和订正数据。
-- 本迁移按预发布空库编写；若表内已有数据，请先备份并补齐 userId/attemptId 后再执行。

-- 1. 本地账号体系 -------------------------------------------------------------
ALTER TABLE `UserCredential`
  ADD COLUMN `username` VARCHAR(64) NULL,
  ADD COLUMN `passwordHash` VARCHAR(255) NULL,
  ADD COLUMN `displayName` VARCHAR(120) NULL,
  ADD COLUMN `email` VARCHAR(255) NULL,
  ADD COLUMN `role` ENUM('ADMIN', 'TEACHER', 'STUDENT', 'RESEARCHER') NOT NULL DEFAULT 'STUDENT',
  ADD COLUMN `status` ENUM('ACTIVE', 'DISABLED', 'LOCKED') NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN `lastLoginAt` DATETIME(3) NULL,
  ADD UNIQUE INDEX `UserCredential_username_key`(`username`),
  ADD UNIQUE INDEX `UserCredential_email_key`(`email`),
  ADD INDEX `UserCredential_role_status_idx`(`role`, `status`);

CREATE TABLE `UserSession` (
  `id` VARCHAR(191) NOT NULL,
  `userId` VARCHAR(191) NOT NULL,
  `tokenHash` VARCHAR(64) NOT NULL,
  `expiresAt` DATETIME(3) NOT NULL,
  `revokedAt` DATETIME(3) NULL,
  `userAgent` VARCHAR(500) NULL,
  `ipAddress` VARCHAR(64) NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `lastSeenAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE INDEX `UserSession_tokenHash_key`(`tokenHash`),
  INDEX `UserSession_userId_idx`(`userId`),
  INDEX `UserSession_expiresAt_idx`(`expiresAt`),
  PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `UserSession`
  ADD CONSTRAINT `UserSession_userId_fkey`
  FOREIGN KEY (`userId`) REFERENCES `UserCredential`(`userId`)
  ON DELETE CASCADE ON UPDATE CASCADE;

-- 2. 知识点说明 ---------------------------------------------------------------
ALTER TABLE `KnowledgePoint`
  ADD COLUMN `description` VARCHAR(500) NULL;

-- 3. attempt 增加班级快照 -----------------------------------------------------
ALTER TABLE `AssignmentAttempt`
  ADD COLUMN `classId` INT NULL,
  ADD INDEX `AssignmentAttempt_classId_assignmentId_submitted_createdAt_idx`(`classId`, `assignmentId`, `submitted`, `createdAt`),
  ADD CONSTRAINT `AssignmentAttempt_classId_fkey`
    FOREIGN KEY (`classId`) REFERENCES `Class`(`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- 4. 同一 attempt 每题只允许一条作答 -----------------------------------------
ALTER TABLE `QuestionResponse`
  ADD UNIQUE INDEX `QuestionResponse_assignmentAttemptId_questionId_key`(`assignmentAttemptId`, `questionId`);

ALTER TABLE `QuestionResponse`
  DROP INDEX `QuestionResponse_assignmentAttemptId_idx`;

-- 5. 学生成绩快照：补充学生、attempt、班级和分数列 ---------------------------
ALTER TABLE `math_ai_student_submission`
  DROP PRIMARY KEY,
  ADD COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT FIRST,
  ADD COLUMN `classId` INT NULL AFTER `assignmentId`,
  ADD COLUMN `userId` VARCHAR(191) NOT NULL AFTER `classId`,
  ADD COLUMN `attemptId` INT NOT NULL AFTER `userId`,
  ADD COLUMN `score` INT NOT NULL DEFAULT 0 AFTER `attemptId`,
  ADD COLUMN `maxScore` INT NOT NULL DEFAULT 0 AFTER `score`,
  ADD COLUMN `scoreRate` DECIMAL(5,2) NOT NULL DEFAULT 0.00 AFTER `maxScore`,
  ADD COLUMN `grade` DECIMAL(6,4) NOT NULL DEFAULT 0.0000 AFTER `scoreRate`,
  ADD COLUMN `passed` BOOLEAN NOT NULL DEFAULT FALSE AFTER `grade`,
  ADD COLUMN `submittedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER `resultJson`,
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE INDEX `math_ai_student_submission_attemptId_key`(`attemptId`),
  ADD INDEX `math_ai_student_submission_assignmentId_userId_submittedAt_idx`(`assignmentId`, `userId`, `submittedAt`),
  ADD INDEX `math_ai_student_submission_classId_assignmentId_submittedAt_idx`(`classId`, `assignmentId`, `submittedAt`),
  MODIFY COLUMN `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE `math_ai_student_submission`
  ADD CONSTRAINT `math_ai_student_submission_assignmentId_fkey`
    FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `math_ai_student_submission_classId_fkey`
    FOREIGN KEY (`classId`) REFERENCES `Class`(`id`)
    ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `math_ai_student_submission_attemptId_fkey`
    FOREIGN KEY (`attemptId`) REFERENCES `AssignmentAttempt`(`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- 6. 订正记录：补充学生、题目、确认信息 -------------------------------------
ALTER TABLE `math_ai_student_correction`
  ADD COLUMN `classId` INT NULL AFTER `assignmentId`,
  ADD COLUMN `userId` VARCHAR(191) NOT NULL AFTER `classId`,
  ADD COLUMN `attemptId` INT NOT NULL AFTER `userId`,
  ADD COLUMN `questionId` INT NOT NULL AFTER `attemptId`,
  ADD COLUMN `selectedChoiceId` INT NULL AFTER `questionId`,
  ADD COLUMN `selectedLabel` VARCHAR(255) NULL AFTER `selectedChoiceId`,
  ADD COLUMN `correctLabel` VARCHAR(255) NULL AFTER `selectedLabel`,
  ADD COLUMN `isCorrect` BOOLEAN NOT NULL DEFAULT FALSE AFTER `correctLabel`,
  ADD COLUMN `explanation` TEXT NULL AFTER `isCorrect`,
  ADD COLUMN `confirmedBy` VARCHAR(191) NULL AFTER `submittedAt`,
  ADD COLUMN `confirmedAt` DATETIME(3) NULL AFTER `confirmedBy`,
  ADD COLUMN `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER `confirmedAt`,
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
  ADD UNIQUE INDEX `math_ai_student_correction_attemptId_questionId_userId_key`(`attemptId`, `questionId`, `userId`),
  ADD INDEX `math_ai_student_correction_assignmentId_status_submittedAt_idx`(`assignmentId`, `status`, `submittedAt`),
  ADD INDEX `math_ai_student_correction_userId_assignmentId_idx`(`userId`, `assignmentId`),
  ADD INDEX `math_ai_student_correction_questionId_idx`(`questionId`);

ALTER TABLE `math_ai_student_correction`
  DROP INDEX `math_ai_student_correction_assignmentId_idx`;

ALTER TABLE `math_ai_student_correction`
  ADD CONSTRAINT `math_ai_student_correction_assignmentId_fkey`
    FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `math_ai_student_correction_attemptId_fkey`
    FOREIGN KEY (`attemptId`) REFERENCES `AssignmentAttempt`(`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `math_ai_student_correction_questionId_fkey`
    FOREIGN KEY (`questionId`) REFERENCES `Question`(`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `math_ai_student_correction_classId_fkey`
    FOREIGN KEY (`classId`) REFERENCES `Class`(`id`)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- 7. 讲评当前指针 -------------------------------------------------------------
ALTER TABLE `math_ai_review_draft`
  ADD COLUMN `createdBy` VARCHAR(191) NULL AFTER `version`,
  ADD COLUMN `updatedBy` VARCHAR(191) NULL AFTER `createdBy`,
  ADD COLUMN `publishedAt` DATETIME(3) NULL AFTER `updatedBy`,
  ADD COLUMN `publishedVersion` INT NULL AFTER `publishedAt`,
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  MODIFY COLUMN `version` INT NOT NULL DEFAULT 1,
  MODIFY COLUMN `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE `math_ai_review_draft`
  ADD CONSTRAINT `math_ai_review_draft_assignmentId_fkey`
    FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;

-- 8. 讲评版本历史 -------------------------------------------------------------
CREATE TABLE `math_ai_review_version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `assignmentId` INT NOT NULL,
  `version` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `contentJson` JSON NOT NULL,
  `evidenceJson` JSON NULL,
  `createdBy` VARCHAR(191) NOT NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `publishedAt` DATETIME(3) NULL,
  UNIQUE INDEX `math_ai_review_version_assignmentId_version_key`(`assignmentId`, `version`),
  INDEX `math_ai_review_version_assignmentId_status_createdAt_idx`(`assignmentId`, `status`, `createdAt`),
  PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `math_ai_review_version`
  ADD CONSTRAINT `math_ai_review_version_assignmentId_fkey`
    FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;
