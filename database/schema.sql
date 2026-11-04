CREATE DATABASE IF NOT EXISTS `mark`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `mark`;

CREATE TABLE IF NOT EXISTS `assignment` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(191) NOT NULL,
  `introduction` TEXT NULL,
  `instructions` TEXT NULL,
  `gradingCriteriaOverview` TEXT NULL,
  `timeEstimateMinutes` INT NOT NULL DEFAULT 10,
  `passingGrade` INT NOT NULL DEFAULT 60,
  `published` TINYINT(1) NOT NULL DEFAULT 0,
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `question` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `assignmentId` INT NOT NULL,
  `type` VARCHAR(32) NOT NULL,
  `question` TEXT NOT NULL,
  `authorComment` TEXT NULL,
  `totalPoints` INT NOT NULL DEFAULT 1,
  `choices` JSON NULL,
  `answer` TINYINT(1) NULL,
  `randomizedChoices` TINYINT(1) NOT NULL DEFAULT 0,
  `isDeleted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `question_assignment_idx` (`assignmentId`, `isDeleted`),
  CONSTRAINT `question_assignment_fk`
    FOREIGN KEY (`assignmentId`) REFERENCES `assignment` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `class` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(191) NOT NULL,
  `classCode` VARCHAR(64) NULL,
  `subject` VARCHAR(64) NULL,
  `term` VARCHAR(64) NULL,
  `gradeLevel` VARCHAR(64) NULL,
  `ownerUserId` VARCHAR(191) NOT NULL DEFAULT 'teacher@local.demo',
  `isArchived` TINYINT(1) NOT NULL DEFAULT 0,
  `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `class_code_unique` (`classCode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `classenrollment` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `classId` INT NOT NULL,
  `studentKey` VARCHAR(96) NOT NULL,
  `studentNo` VARCHAR(64) NULL,
  `displayName` VARCHAR(96) NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `joinedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `class_student_unique` (`classId`, `studentKey`),
  CONSTRAINT `classenrollment_class_fk`
    FOREIGN KEY (`classId`) REFERENCES `class` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `assignmentclass` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `assignmentId` INT NOT NULL,
  `classId` INT NOT NULL,
  `assignedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `assignment_class_unique` (`assignmentId`, `classId`),
  CONSTRAINT `assignmentclass_assignment_fk`
    FOREIGN KEY (`assignmentId`) REFERENCES `assignment` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `assignmentclass_class_fk`
    FOREIGN KEY (`classId`) REFERENCES `class` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `knowledgepoint` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(191) NOT NULL,
  `code` VARCHAR(64) NULL,
  `parentId` INT NULL,
  `subject` VARCHAR(64) NULL,
  `level` INT NOT NULL DEFAULT 1,
  `isActive` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `knowledgepoint_code_unique` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `questionknowledgepoint` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `questionId` INT NOT NULL,
  `knowledgePointId` INT NOT NULL,
  `weight` DOUBLE NOT NULL DEFAULT 1,
  `isPrimary` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `question_knowledge_unique` (`questionId`, `knowledgePointId`),
  CONSTRAINT `questionknowledge_question_fk`
    FOREIGN KEY (`questionId`) REFERENCES `question` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `questionknowledge_point_fk`
    FOREIGN KEY (`knowledgePointId`) REFERENCES `knowledgepoint` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `math_ai_review_draft` (
  `assignmentId` INT NOT NULL,
  `draftJson` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `version` INT NOT NULL,
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`assignmentId`),
  CONSTRAINT `review_assignment_fk`
    FOREIGN KEY (`assignmentId`) REFERENCES `assignment` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `math_ai_student_submission` (
  `studentKey` VARCHAR(96) NOT NULL,
  `assignmentId` INT NOT NULL,
  `resultJson` JSON NOT NULL,
  `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`studentKey`, `assignmentId`),
  KEY `submission_assignment_idx` (`assignmentId`),
  CONSTRAINT `submission_assignment_fk`
    FOREIGN KEY (`assignmentId`) REFERENCES `assignment` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `math_ai_student_correction` (
  `id` VARCHAR(64) NOT NULL,
  `studentKey` VARCHAR(96) NOT NULL,
  `assignmentId` INT NOT NULL,
  `recordJson` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `submittedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `correction_student_assignment_idx` (`studentKey`, `assignmentId`),
  CONSTRAINT `correction_assignment_fk`
    FOREIGN KEY (`assignmentId`) REFERENCES `assignment` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
