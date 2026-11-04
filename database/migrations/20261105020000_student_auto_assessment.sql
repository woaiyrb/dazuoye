USE `mark`;

CREATE TABLE IF NOT EXISTS `student_homework_submission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `homeworkId` INT NOT NULL,
  `studentId` INT NOT NULL,
  `attemptNo` INT NOT NULL,
  `score` INT NOT NULL DEFAULT 0,
  `maxScore` INT NOT NULL DEFAULT 0,
  `scoreRate` DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
  `submittedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `student_homework_attempt_key`
    (`studentId`, `homeworkId`, `attemptNo`),
  KEY `student_submission_homework_idx` (`homeworkId`, `submittedAt`),
  KEY `student_submission_student_idx` (`studentId`, `submittedAt`),
  CONSTRAINT `student_submission_homework_fk`
    FOREIGN KEY (`homeworkId`) REFERENCES `course_homework` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `student_submission_student_fk`
    FOREIGN KEY (`studentId`) REFERENCES `app_user` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `student_question_answer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `submissionId` BIGINT NOT NULL,
  `questionId` INT NOT NULL,
  `questionStem` TEXT NOT NULL,
  `questionType` VARCHAR(32) NOT NULL,
  `selectedAnswer` VARCHAR(500) NULL,
  `correctAnswer` VARCHAR(500) NULL,
  `teacherAnalysis` TEXT NULL,
  `isCorrect` TINYINT(1) NOT NULL DEFAULT 0,
  `awardedScore` DECIMAL(8,2) NOT NULL DEFAULT 0,
  `maxScore` DECIMAL(8,2) NOT NULL DEFAULT 0,
  `answeredAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `student_answer_submission_question_key`
    (`submissionId`, `questionId`),
  KEY `student_answer_question_idx` (`questionId`),
  CONSTRAINT `student_answer_submission_fk`
    FOREIGN KEY (`submissionId`) REFERENCES `student_homework_submission` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `student_answer_question_fk`
    FOREIGN KEY (`questionId`) REFERENCES `course_question` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
