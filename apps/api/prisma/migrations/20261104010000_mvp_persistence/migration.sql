CREATE TABLE `math_ai_review_draft` (
    `assignmentId` INTEGER NOT NULL,
    `draftJson` JSON NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `version` INTEGER NOT NULL,
    `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`assignmentId`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `math_ai_student_submission` (
    `assignmentId` INTEGER NOT NULL,
    `resultJson` JSON NOT NULL,
    `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`assignmentId`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `math_ai_student_correction` (
    `id` VARCHAR(64) NOT NULL,
    `assignmentId` INTEGER NOT NULL,
    `recordJson` JSON NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `submittedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX `math_ai_student_correction_assignmentId_idx`(`assignmentId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
