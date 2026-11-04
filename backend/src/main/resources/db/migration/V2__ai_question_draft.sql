USE `mark`;

CREATE TABLE IF NOT EXISTS `ai_question_draft` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `homeworkId` INT NOT NULL,
  `createdBy` INT NOT NULL,
  `topic` VARCHAR(500) NOT NULL,
  `difficulty` VARCHAR(32) NOT NULL,
  `questionType` VARCHAR(32) NOT NULL,
  `stem` TEXT NOT NULL,
  `options` JSON NULL,
  `standardAnswer` VARCHAR(255) NULL,
  `teacherAnalysis` TEXT NULL,
  `score` INT NOT NULL DEFAULT 1,
  `knowledgePointIds` JSON NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'READY',
  `sourceModel` VARCHAR(128) NULL,
  `rawResponse` LONGTEXT NULL,
  `importedQuestionId` INT NULL,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `ai_question_draft_homework_status_idx`
    (`homeworkId`, `status`, `createdAt`),
  CONSTRAINT `ai_question_draft_homework_fk`
    FOREIGN KEY (`homeworkId`) REFERENCES `course_homework` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `ai_question_draft_creator_fk`
    FOREIGN KEY (`createdBy`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
