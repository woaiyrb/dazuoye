USE `mark`;

CREATE TABLE IF NOT EXISTS `app_user` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `passwordHash` VARCHAR(255) NOT NULL,
  `displayName` VARCHAR(120) NOT NULL,
  `role` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `studentNo` VARCHAR(64) NULL,
  `classId` INT NULL,
  `lastLoginAt` DATETIME(3) NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `app_user_username_key` (`username`),
  UNIQUE KEY `app_user_studentNo_key` (`studentNo`),
  KEY `app_user_role_status_idx` (`role`, `status`),
  KEY `app_user_class_idx` (`classId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `auth_session` (
  `id` VARCHAR(64) NOT NULL,
  `userId` INT NOT NULL,
  `tokenHash` CHAR(64) NOT NULL,
  `expiresAt` DATETIME(3) NOT NULL,
  `revokedAt` DATETIME(3) NULL,
  `userAgent` VARCHAR(500) NULL,
  `ipAddress` VARCHAR(64) NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `lastSeenAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `auth_session_token_key` (`tokenHash`),
  KEY `auth_session_user_idx` (`userId`),
  KEY `auth_session_expiry_idx` (`expiresAt`),
  CONSTRAINT `auth_session_user_fk`
    FOREIGN KEY (`userId`) REFERENCES `app_user` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `classroom` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(191) NOT NULL,
  `grade` VARCHAR(64) NOT NULL DEFAULT '',
  `subject` VARCHAR(64) NOT NULL DEFAULT '数学',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `teacherId` INT NOT NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `classroom_teacher_idx` (`teacherId`),
  CONSTRAINT `classroom_teacher_fk`
    FOREIGN KEY (`teacherId`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `course_homework` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `classId` INT NOT NULL,
  `teacherId` INT NOT NULL,
  `title` VARCHAR(191) NOT NULL,
  `description` TEXT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `dueAt` DATETIME(3) NULL,
  `publishedAt` DATETIME(3) NULL,
  `archivedAt` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 1,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `homework_class_status_idx` (`classId`, `status`),
  CONSTRAINT `homework_class_fk`
    FOREIGN KEY (`classId`) REFERENCES `classroom` (`id`),
  CONSTRAINT `homework_teacher_fk`
    FOREIGN KEY (`teacherId`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `course_question` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `homeworkId` INT NOT NULL,
  `seqNo` INT NOT NULL,
  `questionType` VARCHAR(32) NOT NULL,
  `stem` TEXT NOT NULL,
  `options` JSON NULL,
  `standardAnswer` VARCHAR(255) NULL,
  `teacherAnalysis` TEXT NULL,
  `score` INT NOT NULL DEFAULT 1,
  `revisionNo` INT NOT NULL DEFAULT 1,
  `isDeleted` TINYINT(1) NOT NULL DEFAULT 0,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `course_question_homework_seq_key` (`homeworkId`, `seqNo`),
  CONSTRAINT `course_question_homework_fk`
    FOREIGN KEY (`homeworkId`) REFERENCES `course_homework` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `kp_node` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `parentId` INT NULL,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(191) NOT NULL,
  `kpLevel` INT NOT NULL DEFAULT 1,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `disabledImpactNote` VARCHAR(500) NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `kp_node_code_key` (`code`),
  KEY `kp_node_parent_idx` (`parentId`),
  CONSTRAINT `kp_node_parent_fk`
    FOREIGN KEY (`parentId`) REFERENCES `kp_node` (`id`)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `course_question_kp` (
  `questionId` INT NOT NULL,
  `knowledgePointId` INT NOT NULL,
  PRIMARY KEY (`questionId`, `knowledgePointId`),
  CONSTRAINT `course_question_kp_question_fk`
    FOREIGN KEY (`questionId`) REFERENCES `course_question` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `course_question_kp_point_fk`
    FOREIGN KEY (`knowledgePointId`) REFERENCES `kp_node` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `grading_batch` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `homeworkId` INT NOT NULL,
  `versionNo` INT NOT NULL,
  `source` VARCHAR(32) NOT NULL,
  `importedBy` INT NOT NULL,
  `totalRows` INT NOT NULL,
  `validRows` INT NOT NULL,
  `remark` VARCHAR(500) NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `grading_batch_homework_version_key` (`homeworkId`, `versionNo`),
  KEY `grading_batch_current_idx` (`homeworkId`, `createdAt`),
  CONSTRAINT `grading_batch_homework_fk`
    FOREIGN KEY (`homeworkId`) REFERENCES `course_homework` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `grading_record` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `batchId` INT NOT NULL,
  `homeworkId` INT NOT NULL,
  `studentId` INT NOT NULL,
  `questionId` INT NOT NULL,
  `seqNo` INT NOT NULL,
  `studentAnswer` VARCHAR(500) NULL,
  `isCorrect` TINYINT(1) NOT NULL DEFAULT 0,
  `score` DECIMAL(8,2) NOT NULL DEFAULT 0,
  `teacherNote` VARCHAR(1000) NULL,
  `errorTag` VARCHAR(100) NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `grading_record_batch_question_student_key`
    (`batchId`, `questionId`, `studentId`),
  KEY `grading_record_homework_student_idx` (`homeworkId`, `studentId`),
  CONSTRAINT `grading_record_batch_fk`
    FOREIGN KEY (`batchId`) REFERENCES `grading_batch` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `grading_record_homework_fk`
    FOREIGN KEY (`homeworkId`) REFERENCES `course_homework` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `grading_record_question_fk`
    FOREIGN KEY (`questionId`) REFERENCES `course_question` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `grading_record_student_fk`
    FOREIGN KEY (`studentId`) REFERENCES `app_user` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_comment_draft` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `homeworkId` INT NOT NULL,
  `questionId` INT NULL,
  `draftType` VARCHAR(64) NOT NULL DEFAULT 'QUESTION_COMMENTARY',
  `content` TEXT NULL,
  `status` VARCHAR(32) NOT NULL,
  `model` VARCHAR(128) NULL,
  `promptVersion` VARCHAR(64) NULL,
  `generationNo` INT NOT NULL DEFAULT 1,
  `requestId` VARCHAR(128) NULL,
  `elapsedMs` INT NULL,
  `errorCode` VARCHAR(64) NULL,
  `evidence` JSON NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `ai_draft_homework_question_idx` (`homeworkId`, `questionId`),
  CONSTRAINT `ai_draft_homework_fk`
    FOREIGN KEY (`homeworkId`) REFERENCES `course_homework` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `ai_draft_question_fk`
    FOREIGN KEY (`questionId`) REFERENCES `course_question` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `handout` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `homeworkId` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
  `version` INT NOT NULL DEFAULT 1,
  `gradingBatchId` INT NULL,
  `publishedAt` DATETIME(3) NULL,
  `publishedBy` INT NULL,
  `withdrawnAt` DATETIME(3) NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `handout_homework_key` (`homeworkId`),
  CONSTRAINT `handout_homework_fk`
    FOREIGN KEY (`homeworkId`) REFERENCES `course_homework` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `handout_item` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `handoutId` INT NOT NULL,
  `questionId` INT NOT NULL,
  `source` VARCHAR(32) NULL,
  `status` VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
  `sortNo` INT NOT NULL,
  `currentVersionId` INT NULL,
  `confirmedBy` INT NULL,
  `confirmedAt` DATETIME(3) NULL,
  `excludeReason` VARCHAR(500) NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `handout_item_handout_question_key` (`handoutId`, `questionId`),
  CONSTRAINT `handout_item_handout_fk`
    FOREIGN KEY (`handoutId`) REFERENCES `handout` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `handout_item_question_fk`
    FOREIGN KEY (`questionId`) REFERENCES `course_question` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `handout_item_version` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `itemId` INT NOT NULL,
  `versionNo` INT NOT NULL,
  `content` TEXT NOT NULL,
  `changeType` VARCHAR(64) NOT NULL,
  `changeNote` VARCHAR(500) NULL,
  `createdBy` INT NOT NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `handout_item_version_key` (`itemId`, `versionNo`),
  CONSTRAINT `handout_item_version_item_fk`
    FOREIGN KEY (`itemId`) REFERENCES `handout_item` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `correction_v2` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `homeworkId` INT NOT NULL,
  `questionId` INT NOT NULL,
  `studentId` INT NOT NULL,
  `handoutItemId` INT NOT NULL,
  `itemVersionId` INT NOT NULL,
  `selectedAnswer` VARCHAR(500) NULL,
  `content` TEXT NULL,
  `versionNo` INT NOT NULL DEFAULT 1,
  `submittedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `correction_v2_homework_student_idx` (`homeworkId`, `studentId`),
  CONSTRAINT `correction_v2_homework_fk`
    FOREIGN KEY (`homeworkId`) REFERENCES `course_homework` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `correction_v2_question_fk`
    FOREIGN KEY (`questionId`) REFERENCES `course_question` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `correction_v2_student_fk`
    FOREIGN KEY (`studentId`) REFERENCES `app_user` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `doubt` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `studentId` INT NOT NULL,
  `handoutItemId` INT NOT NULL,
  `questionId` INT NOT NULL,
  `content` TEXT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `doubt_student_status_idx` (`studentId`, `status`),
  CONSTRAINT `doubt_student_fk`
    FOREIGN KEY (`studentId`) REFERENCES `app_user` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `doubt_item_fk`
    FOREIGN KEY (`handoutItemId`) REFERENCES `handout_item` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `doubt_reply` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `doubtId` INT NOT NULL,
  `teacherId` INT NOT NULL,
  `content` TEXT NOT NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `doubt_reply_doubt_idx` (`doubtId`),
  CONSTRAINT `doubt_reply_doubt_fk`
    FOREIGN KEY (`doubtId`) REFERENCES `doubt` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `doubt_reply_teacher_fk`
    FOREIGN KEY (`teacherId`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `comment_template` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(191) NOT NULL,
  `category` VARCHAR(64) NULL,
  `structure` JSON NULL,
  `content` TEXT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `createdBy` INT NOT NULL,
  `reviewedBy` INT NULL,
  `reviewNote` VARCHAR(500) NULL,
  `reviewedAt` DATETIME(3) NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `template_status_idx` (`status`),
  CONSTRAINT `template_creator_fk`
    FOREIGN KEY (`createdBy`) REFERENCES `app_user` (`id`),
  CONSTRAINT `template_reviewer_fk`
    FOREIGN KEY (`reviewedBy`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `traceId` VARCHAR(64) NOT NULL,
  `actorId` INT NULL,
  `actorRole` VARCHAR(32) NULL,
  `actorName` VARCHAR(120) NULL,
  `method` VARCHAR(16) NOT NULL,
  `path` VARCHAR(500) NOT NULL,
  `action` VARCHAR(128) NOT NULL,
  `targetType` VARCHAR(80) NULL,
  `targetId` BIGINT NULL,
  `summary` VARCHAR(500) NULL,
  `requestJson` LONGTEXT NULL,
  `responseJson` LONGTEXT NULL,
  `statusCode` INT NOT NULL,
  `success` TINYINT(1) NOT NULL,
  `durationMs` BIGINT NOT NULL,
  `ipAddress` VARCHAR(64) NULL,
  `userAgent` VARCHAR(500) NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `operation_log_created_idx` (`createdAt`),
  KEY `operation_log_actor_idx` (`actorId`, `createdAt`),
  KEY `operation_log_action_idx` (`action`, `createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `backend_event_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `eventType` VARCHAR(64) NOT NULL,
  `serviceName` VARCHAR(120) NOT NULL,
  `instanceId` VARCHAR(120) NOT NULL,
  `version` VARCHAR(64) NULL,
  `detailJson` JSON NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `backend_event_type_idx` (`eventType`, `createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
