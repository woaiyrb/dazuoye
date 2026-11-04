-- CreateTable
CREATE TABLE `GradingAudit` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `questionId` INTEGER NOT NULL,
    `assignmentId` INTEGER NULL,
    `requestPayload` TEXT NOT NULL,
    `responsePayload` TEXT NOT NULL,
    `gradingStrategy` VARCHAR(191) NOT NULL,
    `metadata` TEXT NULL,
    `timestamp` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `GradingAudit_questionId_idx`(`questionId`),
    INDEX `GradingAudit_assignmentId_idx`(`assignmentId`),
    INDEX `GradingAudit_timestamp_idx`(`timestamp`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `GradingCache` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `cacheKey` VARCHAR(191) NOT NULL,
    `questionId` INTEGER NOT NULL,
    `rubricHash` VARCHAR(191) NOT NULL,
    `answerHash` VARCHAR(191) NOT NULL,
    `totalScore` DOUBLE NOT NULL,
    `maxScore` DOUBLE NOT NULL,
    `criteria` JSON NOT NULL,
    `overallFeedback` TEXT NOT NULL,
    `cachedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `hitCount` INTEGER NOT NULL DEFAULT 0,
    `metadata` JSON NULL,

    UNIQUE INDEX `GradingCache_cacheKey_key`(`cacheKey`),
    INDEX `GradingCache_questionId_idx`(`questionId`),
    INDEX `GradingCache_rubricHash_idx`(`rubricHash`),
    INDEX `GradingCache_answerHash_idx`(`answerHash`),
    INDEX `GradingCache_cachedAt_idx`(`cachedAt`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `Job` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `userId` VARCHAR(191) NOT NULL,
    `assignmentId` INTEGER NOT NULL,
    `status` VARCHAR(191) NOT NULL,
    `progress` VARCHAR(191) NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,
    `result` JSON NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `GradingJob` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `attemptId` INTEGER NULL,
    `assignmentId` INTEGER NOT NULL,
    `userId` VARCHAR(191) NOT NULL,
    `status` VARCHAR(191) NOT NULL,
    `progress` VARCHAR(191) NOT NULL,
    `percentage` INTEGER NULL,
    `result` JSON NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    INDEX `GradingJob_attemptId_idx`(`attemptId`),
    INDEX `GradingJob_assignmentId_idx`(`assignmentId`),
    INDEX `GradingJob_userId_idx`(`userId`),
    INDEX `GradingJob_status_idx`(`status`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `publishJob` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `userId` VARCHAR(191) NOT NULL,
    `assignmentId` INTEGER NOT NULL,
    `status` VARCHAR(191) NOT NULL,
    `progress` VARCHAR(191) NULL,
    `percentage` INTEGER NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,
    `result` JSON NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AIUsage` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `usageType` ENUM('QUESTION_GENERATION', 'ASSIGNMENT_GENERATION', 'ASSIGNMENT_GRADING', 'TRANSLATION', 'LIVE_RECORDING_FEEDBACK', 'GRADING_VALIDATION', 'CHAT_ASSISTANT') NOT NULL,
    `tokensIn` BIGINT NOT NULL DEFAULT 0,
    `tokensOut` BIGINT NOT NULL DEFAULT 0,
    `usageCount` BIGINT NOT NULL DEFAULT 0,
    `usageDetails` JSON NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,
    `userId` VARCHAR(191) NULL,
    `modelKey` VARCHAR(191) NULL,

    INDEX `AIUsage_assignmentId_createdAt_usageType_modelKey_idx`(`assignmentId`, `createdAt`, `usageType`, `modelKey`),
    UNIQUE INDEX `AIUsage_assignmentId_usageType_key`(`assignmentId`, `usageType`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `UserCredential` (
    `userId` VARCHAR(191) NOT NULL,
    `githubToken` VARCHAR(191) NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    PRIMARY KEY (`userId`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `Chat` (
    `id` VARCHAR(191) NOT NULL,
    `userId` VARCHAR(191) NOT NULL,
    `startedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `lastActiveAt` DATETIME(3) NOT NULL,
    `title` VARCHAR(191) NULL,
    `assignmentId` INTEGER NULL,
    `isActive` BOOLEAN NOT NULL DEFAULT true,

    INDEX `Chat_userId_startedAt_idx`(`userId`, `startedAt`),
    INDEX `Chat_assignmentId_idx`(`assignmentId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `ChatMessage` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `chatId` VARCHAR(191) NOT NULL,
    `role` ENUM('USER', 'ASSISTANT', 'SYSTEM') NOT NULL,
    `content` TEXT NOT NULL,
    `timestamp` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `toolCalls` JSON NULL,

    INDEX `ChatMessage_chatId_timestamp_idx`(`chatId`, `timestamp`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `Group` (
    `id` VARCHAR(191) NOT NULL,

    UNIQUE INDEX `Group_id_key`(`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AssignmentGroup` (
    `assignmentId` INTEGER NOT NULL,
    `groupId` VARCHAR(191) NOT NULL,

    PRIMARY KEY (`assignmentId`, `groupId`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `Assignment` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `introduction` VARCHAR(191) NULL,
    `instructions` VARCHAR(191) NULL,
    `gradingCriteriaOverview` VARCHAR(191) NULL,
    `timeEstimateMinutes` INTEGER NULL,
    `attemptsBeforeCoolDown` INTEGER NULL DEFAULT 1,
    `retakeAttemptCoolDownMinutes` INTEGER NULL DEFAULT 5,
    `type` ENUM('AI_GRADED', 'MANUAL') NOT NULL,
    `graded` BOOLEAN NULL DEFAULT false,
    `numAttempts` INTEGER NULL DEFAULT -1,
    `allotedTimeMinutes` INTEGER NULL,
    `requireAllQuestions` BOOLEAN NOT NULL DEFAULT false,
    `optionalQuestionIds` JSON NULL,
    `attemptsPerTimeRange` INTEGER NULL,
    `attemptsTimeRangeHours` INTEGER NULL,
    `passingGrade` INTEGER NULL DEFAULT 50,
    `displayOrder` ENUM('DEFINED', 'RANDOM') NULL,
    `questionDisplay` ENUM('ONE_PER_PAGE', 'ALL_PER_PAGE') NULL DEFAULT 'ONE_PER_PAGE',
    `numberOfQuestionsPerAttempt` INTEGER NULL,
    `questionOrder` JSON NULL,
    `published` BOOLEAN NOT NULL,
    `showAssignmentScore` BOOLEAN NOT NULL DEFAULT true,
    `showQuestionScore` BOOLEAN NOT NULL DEFAULT true,
    `showSubmissionFeedback` BOOLEAN NOT NULL DEFAULT true,
    `showQuestions` BOOLEAN NOT NULL DEFAULT true,
    `showPassFailIndicator` BOOLEAN NOT NULL DEFAULT false,
    `correctAnswerVisibility` ENUM('NEVER', 'ALWAYS', 'ON_PASS') NOT NULL DEFAULT 'ALWAYS',
    `questionControls` JSON NULL,
    `updatedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `languageCode` VARCHAR(191) NULL,
    `currentVersionId` INTEGER NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AssignmentFile` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `filename` VARCHAR(191) NOT NULL,
    `mimeType` VARCHAR(191) NOT NULL,
    `size` INTEGER NOT NULL,
    `storageKey` VARCHAR(191) NOT NULL,
    `storageBucket` VARCHAR(191) NOT NULL,
    `status` ENUM('UPLOADING', 'READY', 'FAILED') NOT NULL DEFAULT 'READY',
    `uploadId` VARCHAR(191) NULL,
    `extractedText` TEXT NULL,
    `extractionStatus` ENUM('PENDING', 'READY', 'FAILED') NOT NULL DEFAULT 'PENDING',
    `extractionError` TEXT NULL,
    `extractedAt` DATETIME(3) NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    INDEX `AssignmentFile_assignmentId_idx`(`assignmentId`),
    INDEX `AssignmentFile_assignmentId_status_idx`(`assignmentId`, `status`),
    INDEX `AssignmentFile_assignmentId_extractionStatus_idx`(`assignmentId`, `extractionStatus`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AssignmentVersion` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `versionNumber` VARCHAR(191) NOT NULL,
    `name` VARCHAR(191) NOT NULL,
    `introduction` VARCHAR(191) NULL,
    `instructions` VARCHAR(191) NULL,
    `gradingCriteriaOverview` VARCHAR(191) NULL,
    `timeEstimateMinutes` INTEGER NULL,
    `attemptsBeforeCoolDown` INTEGER NULL DEFAULT 1,
    `retakeAttemptCoolDownMinutes` INTEGER NULL DEFAULT 5,
    `type` ENUM('AI_GRADED', 'MANUAL') NOT NULL,
    `graded` BOOLEAN NULL DEFAULT false,
    `numAttempts` INTEGER NULL DEFAULT -1,
    `requireAllQuestions` BOOLEAN NOT NULL DEFAULT false,
    `optionalQuestionIds` JSON NULL,
    `allotedTimeMinutes` INTEGER NULL,
    `attemptsPerTimeRange` INTEGER NULL,
    `attemptsTimeRangeHours` INTEGER NULL,
    `passingGrade` INTEGER NULL DEFAULT 50,
    `displayOrder` ENUM('DEFINED', 'RANDOM') NULL,
    `questionDisplay` ENUM('ONE_PER_PAGE', 'ALL_PER_PAGE') NULL DEFAULT 'ONE_PER_PAGE',
    `numberOfQuestionsPerAttempt` INTEGER NULL,
    `questionOrder` JSON NULL,
    `published` BOOLEAN NOT NULL DEFAULT false,
    `showAssignmentScore` BOOLEAN NOT NULL DEFAULT true,
    `showQuestionScore` BOOLEAN NOT NULL DEFAULT true,
    `showSubmissionFeedback` BOOLEAN NOT NULL DEFAULT true,
    `showQuestions` BOOLEAN NOT NULL DEFAULT true,
    `showPassFailIndicator` BOOLEAN NOT NULL DEFAULT false,
    `correctAnswerVisibility` ENUM('NEVER', 'ALWAYS', 'ON_PASS') NOT NULL DEFAULT 'ALWAYS',
    `questionControls` JSON NULL,
    `languageCode` VARCHAR(191) NULL,
    `createdBy` VARCHAR(191) NOT NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `isDraft` BOOLEAN NOT NULL DEFAULT true,
    `versionDescription` VARCHAR(191) NULL,
    `isActive` BOOLEAN NOT NULL DEFAULT false,

    INDEX `AssignmentVersion_assignmentId_idx`(`assignmentId`),
    INDEX `AssignmentVersion_isActive_idx`(`isActive`),
    INDEX `AssignmentVersion_isDraft_idx`(`isDraft`),
    UNIQUE INDEX `AssignmentVersion_assignmentId_versionNumber_key`(`assignmentId`, `versionNumber`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `QuestionVersion` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentVersionId` INTEGER NOT NULL,
    `authorComment` VARCHAR(191) NULL,
    `questionId` INTEGER NULL,
    `totalPoints` INTEGER NOT NULL,
    `type` ENUM('TEXT', 'SINGLE_CORRECT', 'MULTIPLE_CORRECT', 'TRUE_FALSE', 'URL', 'UPLOAD', 'LINK_FILE') NOT NULL,
    `responseType` ENUM('REPO', 'CODE', 'ESSAY', 'REPORT', 'PRESENTATION', 'VIDEO', 'AUDIO', 'IMAGES', 'SPREADSHEET', 'LIVE_RECORDING', 'OTHER') NULL,
    `question` VARCHAR(191) NOT NULL,
    `maxWords` INTEGER NULL,
    `scoring` JSON NULL,
    `choices` JSON NULL,
    `randomizedChoices` BOOLEAN NULL,
    `answer` BOOLEAN NULL,
    `gradingContextQuestionIds` JSON NULL,
    `maxCharacters` INTEGER NULL,
    `videoPresentationConfig` JSON NULL,
    `liveRecordingConfig` JSON NULL,
    `displayOrder` INTEGER NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `QuestionVersion_assignmentVersionId_idx`(`assignmentVersionId`),
    INDEX `QuestionVersion_questionId_idx`(`questionId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `VersionHistory` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `fromVersionId` INTEGER NULL,
    `toVersionId` INTEGER NOT NULL,
    `action` VARCHAR(191) NOT NULL,
    `description` VARCHAR(191) NULL,
    `userId` VARCHAR(191) NOT NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `metadata` JSON NULL,

    INDEX `VersionHistory_assignmentId_idx`(`assignmentId`),
    INDEX `VersionHistory_createdAt_idx`(`createdAt`),
    INDEX `VersionHistory_userId_idx`(`userId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `Question` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `totalPoints` INTEGER NOT NULL,
    `type` ENUM('TEXT', 'SINGLE_CORRECT', 'MULTIPLE_CORRECT', 'TRUE_FALSE', 'URL', 'UPLOAD', 'LINK_FILE') NOT NULL,
    `responseType` ENUM('REPO', 'CODE', 'ESSAY', 'REPORT', 'PRESENTATION', 'VIDEO', 'AUDIO', 'IMAGES', 'SPREADSHEET', 'LIVE_RECORDING', 'OTHER') NULL,
    `authorComment` VARCHAR(191) NULL,
    `question` VARCHAR(191) NOT NULL,
    `maxWords` INTEGER NULL,
    `scoring` JSON NULL,
    `choices` JSON NULL,
    `randomizedChoices` BOOLEAN NULL,
    `answer` BOOLEAN NULL,
    `assignmentId` INTEGER NOT NULL,
    `gradingContextQuestionIds` JSON NULL,
    `maxCharacters` INTEGER NULL,
    `isDeleted` BOOLEAN NOT NULL DEFAULT false,
    `videoPresentationConfig` JSON NULL,
    `liveRecordingConfig` JSON NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AssignmentAttempt` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `assignmentVersionId` INTEGER NULL,
    `userId` VARCHAR(191) NOT NULL,
    `submitted` BOOLEAN NOT NULL,
    `grade` DOUBLE NULL,
    `expiresAt` DATETIME(3) NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `questionOrder` JSON NULL,
    `comments` VARCHAR(191) NULL,
    `preferredLanguage` VARCHAR(191) NULL,

    INDEX `AssignmentAttempt_assignmentId_idx`(`assignmentId`),
    INDEX `AssignmentAttempt_userId_idx`(`userId`),
    INDEX `AssignmentAttempt_assignmentId_submitted_idx`(`assignmentId`, `submitted`),
    INDEX `AssignmentAttempt_createdAt_idx`(`createdAt`),
    INDEX `AssignmentAttempt_assignmentId_userId_idx`(`assignmentId`, `userId`),
    INDEX `AssignmentAttempt_assignmentVersionId_idx`(`assignmentVersionId`),
    INDEX `AssignmentAttempt_assignmentId_submitted_createdAt_idx`(`assignmentId`, `submitted`, `createdAt`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `GradingProgress` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `attemptId` INTEGER NOT NULL,
    `status` ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    `currentQuestion` INTEGER NULL,
    `totalQuestions` INTEGER NOT NULL,
    `currentStage` VARCHAR(191) NULL,
    `progress` INTEGER NOT NULL DEFAULT 0,
    `error` VARCHAR(191) NULL,
    `notifyOnComplete` BOOLEAN NOT NULL DEFAULT false,
    `notificationEmail` VARCHAR(191) NULL,
    `completedAt` DATETIME(3) NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    UNIQUE INDEX `GradingProgress_attemptId_key`(`attemptId`),
    INDEX `GradingProgress_attemptId_idx`(`attemptId`),
    INDEX `GradingProgress_status_idx`(`status`),
    INDEX `GradingProgress_notifyOnComplete_idx`(`notifyOnComplete`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AssignmentAttemptQuestionVariant` (
    `assignmentAttemptId` INTEGER NOT NULL,
    `questionId` INTEGER NOT NULL,
    `questionVariantId` INTEGER NULL,
    `randomizedChoices` JSON NULL,

    PRIMARY KEY (`assignmentAttemptId`, `questionId`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `QuestionVariant` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `questionId` INTEGER NOT NULL,
    `variantContent` VARCHAR(191) NOT NULL,
    `choices` JSON NULL,
    `maxWords` INTEGER NULL,
    `scoring` JSON NULL,
    `answer` BOOLEAN NULL,
    `maxCharacters` INTEGER NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `difficultyLevel` INTEGER NULL,
    `variantType` ENUM('REWORDED', 'RANDOMIZED', 'DIFFICULTY_ADJUSTED') NOT NULL,
    `randomizedChoices` BOOLEAN NULL,
    `isDeleted` BOOLEAN NOT NULL DEFAULT false,

    UNIQUE INDEX `QuestionVariant_questionId_variantContent_key`(`questionId`, `variantContent`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `QuestionResponse` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentAttemptId` INTEGER NOT NULL,
    `questionId` INTEGER NOT NULL,
    `learnerResponse` VARCHAR(191) NOT NULL,
    `points` DOUBLE NOT NULL,
    `feedback` JSON NOT NULL,
    `metadata` JSON NULL,
    `gradedAt` DATETIME(3) NULL,

    INDEX `QuestionResponse_assignmentAttemptId_idx`(`assignmentAttemptId`),
    INDEX `QuestionResponse_questionId_assignmentAttemptId_idx`(`questionId`, `assignmentAttemptId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AssignmentFeedback` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `attemptId` INTEGER NOT NULL,
    `userId` VARCHAR(191) NOT NULL,
    `comments` VARCHAR(191) NULL,
    `aiGradingRating` INTEGER NULL,
    `assignmentRating` INTEGER NULL,
    `aiFeedbackRating` INTEGER NULL,
    `allowContact` BOOLEAN NOT NULL DEFAULT false,
    `firstName` VARCHAR(191) NULL,
    `lastName` VARCHAR(191) NULL,
    `email` VARCHAR(191) NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    INDEX `AssignmentFeedback_assignmentId_idx`(`assignmentId`),
    INDEX `AssignmentFeedback_assignmentId_assignmentRating_idx`(`assignmentId`, `assignmentRating`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `RegradingRequest` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `userId` VARCHAR(191) NOT NULL,
    `attemptId` INTEGER NOT NULL,
    `regradingReason` VARCHAR(191) NULL,
    `regradingStatus` ENUM('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED') NOT NULL DEFAULT 'PENDING',
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `Report` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `reporterId` VARCHAR(191) NOT NULL,
    `assignmentId` INTEGER NULL,
    `attemptId` INTEGER NULL,
    `issueType` ENUM('BUG', 'FEEDBACK', 'SUGGESTION', 'PERFORMANCE', 'FALSE_MARKING', 'OTHER') NOT NULL,
    `description` VARCHAR(191) NOT NULL,
    `author` BOOLEAN NOT NULL DEFAULT false,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,
    `status` ENUM('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED') NOT NULL DEFAULT 'OPEN',
    `issueNumber` INTEGER NULL,
    `statusMessage` VARCHAR(191) NULL,
    `resolution` VARCHAR(191) NULL,
    `comments` VARCHAR(191) NULL,
    `closureReason` VARCHAR(191) NULL,
    `duplicateOfReportId` INTEGER NULL,
    `relatedToReportId` INTEGER NULL,
    `similarityScore` DOUBLE NULL,
    `renewalEmailSentAt` DATETIME(3) NULL,
    `renewalActionAt` DATETIME(3) NULL,
    `renewalAction` VARCHAR(191) NULL,
    `renewalRenewTokenHash` VARCHAR(191) NULL,
    `renewalCloseTokenHash` VARCHAR(191) NULL,
    `renewalTokenExpiresAt` DATETIME(3) NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `ReportDiagnostics` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `reportId` INTEGER NOT NULL,
    `data` JSON NOT NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `ReportDiagnostics_reportId_key`(`reportId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `UserNotification` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `userId` VARCHAR(191) NOT NULL,
    `type` VARCHAR(191) NOT NULL,
    `title` VARCHAR(191) NOT NULL,
    `message` VARCHAR(191) NOT NULL,
    `metadata` TEXT NULL,
    `read` BOOLEAN NOT NULL DEFAULT false,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `LtiGradeSync` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `attemptId` INTEGER NOT NULL,
    `userId` VARCHAR(191) NOT NULL,
    `assignmentId` INTEGER NOT NULL,
    `grade` DOUBLE NOT NULL,
    `status` ENUM('PENDING', 'IN_PROGRESS', 'SUCCESS', 'FAILED', 'SCHEDULED') NOT NULL DEFAULT 'PENDING',
    `retryCount` INTEGER NOT NULL DEFAULT 0,
    `maxRetries` INTEGER NOT NULL DEFAULT 5,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,
    `lastAttemptAt` DATETIME(3) NULL,
    `nextRetryAt` DATETIME(3) NULL,
    `completedAt` DATETIME(3) NULL,
    `lastError` VARCHAR(191) NULL,
    `errorHistory` JSON NULL,
    `authCookie` VARCHAR(191) NOT NULL,
    `ltiGatewayUrl` VARCHAR(191) NULL,
    `learnerNotified` BOOLEAN NOT NULL DEFAULT false,
    `notificationSentAt` DATETIME(3) NULL,

    INDEX `LtiGradeSync_attemptId_idx`(`attemptId`),
    INDEX `LtiGradeSync_userId_idx`(`userId`),
    INDEX `LtiGradeSync_status_idx`(`status`),
    INDEX `LtiGradeSync_nextRetryAt_idx`(`nextRetryAt`),
    INDEX `LtiGradeSync_createdAt_idx`(`createdAt`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `LtiSyncErrorLog` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `syncId` INTEGER NOT NULL,
    `attemptNumber` INTEGER NOT NULL,
    `errorMessage` VARCHAR(191) NOT NULL,
    `errorStack` VARCHAR(191) NULL,
    `httpStatus` INTEGER NULL,
    `responseBody` VARCHAR(191) NULL,
    `timestamp` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `metadata` JSON NULL,

    INDEX `LtiSyncErrorLog_syncId_idx`(`syncId`),
    INDEX `LtiSyncErrorLog_timestamp_idx`(`timestamp`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `Translation` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `questionId` INTEGER NULL,
    `variantId` INTEGER NULL,
    `languageCode` VARCHAR(191) NOT NULL,
    `translatedText` VARCHAR(191) NOT NULL,
    `untranslatedText` VARCHAR(191) NULL,
    `translatedChoices` JSON NULL,
    `untranslatedChoices` JSON NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `Translation_questionId_variantId_languageCode_idx`(`questionId`, `variantId`, `languageCode`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AssignmentDraft` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `userId` VARCHAR(191) NOT NULL,
    `draftName` VARCHAR(191) NOT NULL,
    `name` VARCHAR(191) NOT NULL,
    `introduction` VARCHAR(191) NULL,
    `instructions` VARCHAR(191) NULL,
    `gradingCriteriaOverview` VARCHAR(191) NULL,
    `timeEstimateMinutes` INTEGER NULL,
    `attemptsBeforeCoolDown` INTEGER NULL DEFAULT 1,
    `retakeAttemptCoolDownMinutes` INTEGER NULL DEFAULT 5,
    `type` ENUM('AI_GRADED', 'MANUAL') NOT NULL,
    `graded` BOOLEAN NULL DEFAULT false,
    `numAttempts` INTEGER NULL DEFAULT -1,
    `requireAllQuestions` BOOLEAN NOT NULL DEFAULT false,
    `optionalQuestionIds` JSON NULL,
    `allotedTimeMinutes` INTEGER NULL,
    `attemptsPerTimeRange` INTEGER NULL,
    `attemptsTimeRangeHours` INTEGER NULL,
    `passingGrade` INTEGER NULL DEFAULT 50,
    `displayOrder` ENUM('DEFINED', 'RANDOM') NULL,
    `questionDisplay` ENUM('ONE_PER_PAGE', 'ALL_PER_PAGE') NULL DEFAULT 'ONE_PER_PAGE',
    `numberOfQuestionsPerAttempt` INTEGER NULL,
    `questionOrder` JSON NULL,
    `published` BOOLEAN NOT NULL DEFAULT false,
    `showAssignmentScore` BOOLEAN NOT NULL DEFAULT true,
    `showQuestionScore` BOOLEAN NOT NULL DEFAULT true,
    `showSubmissionFeedback` BOOLEAN NOT NULL DEFAULT true,
    `showQuestions` BOOLEAN NOT NULL DEFAULT true,
    `showPassFailIndicator` BOOLEAN NOT NULL DEFAULT false,
    `languageCode` VARCHAR(191) NULL,
    `questionsData` JSON NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    INDEX `AssignmentDraft_assignmentId_idx`(`assignmentId`),
    INDEX `AssignmentDraft_userId_idx`(`userId`),
    INDEX `AssignmentDraft_createdAt_idx`(`createdAt`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AssignmentTranslation` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `languageCode` VARCHAR(191) NOT NULL,
    `name` VARCHAR(191) NOT NULL,
    `introduction` VARCHAR(191) NOT NULL,
    `instructions` VARCHAR(191) NULL,
    `gradingCriteriaOverview` VARCHAR(191) NULL,
    `translatedName` VARCHAR(191) NULL,
    `translatedIntroduction` VARCHAR(191) NULL,
    `translatedInstructions` VARCHAR(191) NULL,
    `translatedGradingCriteriaOverview` VARCHAR(191) NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    UNIQUE INDEX `AssignmentTranslation_assignmentId_languageCode_key`(`assignmentId`, `languageCode`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `FeedbackTranslation` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `questionId` INTEGER NOT NULL,
    `languageCode` VARCHAR(191) NOT NULL,
    `untranslatedFeedback` JSON NOT NULL,
    `translatedFeedback` JSON NOT NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    UNIQUE INDEX `FeedbackTranslation_questionId_languageCode_key`(`questionId`, `languageCode`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AdminVerificationCode` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(191) NOT NULL,
    `code` VARCHAR(191) NOT NULL,
    `expiresAt` DATETIME(3) NOT NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `used` BOOLEAN NOT NULL DEFAULT false,

    INDEX `AdminVerificationCode_email_idx`(`email`),
    INDEX `AdminVerificationCode_code_idx`(`code`),
    INDEX `AdminVerificationCode_expiresAt_idx`(`expiresAt`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AdminSession` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(191) NOT NULL,
    `sessionToken` VARCHAR(191) NOT NULL,
    `expiresAt` DATETIME(3) NOT NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `AdminSession_sessionToken_key`(`sessionToken`),
    INDEX `AdminSession_email_idx`(`email`),
    INDEX `AdminSession_sessionToken_idx`(`sessionToken`),
    INDEX `AdminSession_expiresAt_idx`(`expiresAt`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AssignmentAuthor` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `userId` VARCHAR(191) NOT NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `AssignmentAuthor_assignmentId_idx`(`assignmentId`),
    INDEX `AssignmentAuthor_userId_idx`(`userId`),
    UNIQUE INDEX `AssignmentAuthor_assignmentId_userId_key`(`assignmentId`, `userId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `LLMModel` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `modelKey` VARCHAR(191) NOT NULL,
    `displayName` VARCHAR(191) NOT NULL,
    `provider` VARCHAR(191) NOT NULL,
    `isActive` BOOLEAN NOT NULL DEFAULT true,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    UNIQUE INDEX `LLMModel_modelKey_key`(`modelKey`),
    INDEX `LLMModel_modelKey_idx`(`modelKey`),
    INDEX `LLMModel_provider_idx`(`provider`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `LLMPricing` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `modelId` INTEGER NOT NULL,
    `inputTokenPrice` DOUBLE NOT NULL,
    `outputTokenPrice` DOUBLE NOT NULL,
    `effectiveDate` DATETIME(3) NOT NULL,
    `source` ENUM('OPENAI_API', 'MANUAL', 'WEB_SCRAPING') NOT NULL,
    `isActive` BOOLEAN NOT NULL DEFAULT true,
    `metadata` JSON NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    INDEX `LLMPricing_modelId_effectiveDate_idx`(`modelId`, `effectiveDate`),
    INDEX `LLMPricing_effectiveDate_idx`(`effectiveDate`),
    INDEX `LLMPricing_isActive_idx`(`isActive`),
    UNIQUE INDEX `LLMPricing_modelId_effectiveDate_source_key`(`modelId`, `effectiveDate`, `source`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `LLMPriceUpscaling` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `globalFactor` DOUBLE NULL,
    `usageTypeFactors` JSON NULL,
    `reason` VARCHAR(191) NULL,
    `appliedBy` VARCHAR(191) NULL,
    `isActive` BOOLEAN NOT NULL DEFAULT true,
    `effectiveDate` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `deactivatedAt` DATETIME(3) NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    INDEX `LLMPriceUpscaling_isActive_effectiveDate_idx`(`isActive`, `effectiveDate`),
    INDEX `LLMPriceUpscaling_effectiveDate_idx`(`effectiveDate`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AIFeature` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `featureKey` VARCHAR(191) NOT NULL,
    `featureType` ENUM('TEXT_GRADING', 'FILE_GRADING', 'IMAGE_GRADING', 'URL_GRADING', 'PRESENTATION_GRADING', 'VIDEO_GRADING', 'QUESTION_GENERATION', 'TRANSLATION', 'RUBRIC_GENERATION', 'CONTENT_MODERATION', 'ASSIGNMENT_GENERATION', 'LIVE_RECORDING_FEEDBACK') NOT NULL,
    `displayName` VARCHAR(191) NOT NULL,
    `description` VARCHAR(191) NULL,
    `isActive` BOOLEAN NOT NULL DEFAULT true,
    `requiresModel` BOOLEAN NOT NULL DEFAULT true,
    `defaultModelKey` VARCHAR(191) NULL,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    UNIQUE INDEX `AIFeature_featureKey_key`(`featureKey`),
    INDEX `AIFeature_featureKey_idx`(`featureKey`),
    INDEX `AIFeature_featureType_idx`(`featureType`),
    INDEX `AIFeature_isActive_idx`(`isActive`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `LLMFeatureAssignment` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `featureId` INTEGER NOT NULL,
    `modelId` INTEGER NOT NULL,
    `isActive` BOOLEAN NOT NULL DEFAULT true,
    `priority` INTEGER NOT NULL DEFAULT 0,
    `assignedBy` VARCHAR(191) NULL,
    `assignedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `deactivatedAt` DATETIME(3) NULL,
    `metadata` JSON NULL,

    INDEX `LLMFeatureAssignment_featureId_isActive_idx`(`featureId`, `isActive`),
    INDEX `LLMFeatureAssignment_modelId_idx`(`modelId`),
    INDEX `LLMFeatureAssignment_isActive_idx`(`isActive`),
    UNIQUE INDEX `LLMFeatureAssignment_featureId_modelId_key`(`featureId`, `modelId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `FileUpload` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `userId` VARCHAR(191) NOT NULL,
    `uploadId` VARCHAR(191) NOT NULL,
    `storageKey` VARCHAR(191) NOT NULL,
    `bucket` VARCHAR(191) NOT NULL,
    `uploadType` VARCHAR(191) NOT NULL,
    `sizeBytes` BIGINT NULL,
    `status` VARCHAR(191) NOT NULL DEFAULT 'PENDING',
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `completedAt` DATETIME(3) NULL,

    UNIQUE INDEX `FileUpload_uploadId_key`(`uploadId`),
    INDEX `FileUpload_userId_idx`(`userId`),
    INDEX `FileUpload_userId_storageKey_idx`(`userId`, `storageKey`),
    INDEX `FileUpload_status_idx`(`status`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `Class` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `classCode` VARCHAR(191) NULL,
    `subject` VARCHAR(191) NULL,
    `term` VARCHAR(191) NULL,
    `gradeLevel` VARCHAR(191) NULL,
    `ownerUserId` VARCHAR(191) NOT NULL,
    `isArchived` BOOLEAN NOT NULL DEFAULT false,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    UNIQUE INDEX `Class_classCode_key`(`classCode`),
    INDEX `Class_ownerUserId_idx`(`ownerUserId`),
    INDEX `Class_isArchived_idx`(`isArchived`),
    INDEX `Class_subject_term_idx`(`subject`, `term`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `ClassTeacher` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `classId` INTEGER NOT NULL,
    `userId` VARCHAR(191) NOT NULL,
    `role` ENUM('OWNER', 'COLLABORATOR') NOT NULL DEFAULT 'COLLABORATOR',
    `canEdit` BOOLEAN NOT NULL DEFAULT true,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `ClassTeacher_userId_idx`(`userId`),
    UNIQUE INDEX `ClassTeacher_classId_userId_key`(`classId`, `userId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `ClassEnrollment` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `classId` INTEGER NOT NULL,
    `userId` VARCHAR(191) NOT NULL,
    `studentNo` VARCHAR(191) NULL,
    `displayName` VARCHAR(191) NULL,
    `status` ENUM('ACTIVE', 'TRANSFERRED_OUT', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    `joinedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `leftAt` DATETIME(3) NULL,

    INDEX `ClassEnrollment_classId_status_idx`(`classId`, `status`),
    INDEX `ClassEnrollment_userId_idx`(`userId`),
    UNIQUE INDEX `ClassEnrollment_classId_userId_key`(`classId`, `userId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `AssignmentClass` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `assignmentId` INTEGER NOT NULL,
    `classId` INTEGER NOT NULL,
    `assignedAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `dueAt` DATETIME(3) NULL,

    INDEX `AssignmentClass_assignmentId_idx`(`assignmentId`),
    INDEX `AssignmentClass_classId_idx`(`classId`),
    UNIQUE INDEX `AssignmentClass_assignmentId_classId_key`(`assignmentId`, `classId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `KnowledgePoint` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `code` VARCHAR(191) NULL,
    `parentId` INTEGER NULL,
    `subject` VARCHAR(191) NULL,
    `level` INTEGER NOT NULL DEFAULT 1,
    `ownerUserId` VARCHAR(191) NULL,
    `isActive` BOOLEAN NOT NULL DEFAULT true,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updatedAt` DATETIME(3) NOT NULL,

    UNIQUE INDEX `KnowledgePoint_code_key`(`code`),
    INDEX `KnowledgePoint_parentId_idx`(`parentId`),
    INDEX `KnowledgePoint_subject_isActive_idx`(`subject`, `isActive`),
    INDEX `KnowledgePoint_ownerUserId_idx`(`ownerUserId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `QuestionKnowledgePoint` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `questionId` INTEGER NOT NULL,
    `knowledgePointId` INTEGER NOT NULL,
    `weight` DOUBLE NOT NULL DEFAULT 1,
    `isPrimary` BOOLEAN NOT NULL DEFAULT false,
    `createdAt` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `QuestionKnowledgePoint_knowledgePointId_idx`(`knowledgePointId`),
    UNIQUE INDEX `QuestionKnowledgePoint_questionId_knowledgePointId_key`(`questionId`, `knowledgePointId`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `_ChatToUserCredential` (
    `A` VARCHAR(191) NOT NULL,
    `B` VARCHAR(191) NOT NULL,

    UNIQUE INDEX `_ChatToUserCredential_AB_unique`(`A`, `B`),
    INDEX `_ChatToUserCredential_B_index`(`B`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `GradingAudit` ADD CONSTRAINT `GradingAudit_questionId_fkey` FOREIGN KEY (`questionId`) REFERENCES `Question`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `GradingAudit` ADD CONSTRAINT `GradingAudit_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `GradingCache` ADD CONSTRAINT `GradingCache_questionId_fkey` FOREIGN KEY (`questionId`) REFERENCES `Question`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `GradingJob` ADD CONSTRAINT `GradingJob_attemptId_fkey` FOREIGN KEY (`attemptId`) REFERENCES `AssignmentAttempt`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `GradingJob` ADD CONSTRAINT `GradingJob_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AIUsage` ADD CONSTRAINT `AIUsage_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AIUsage` ADD CONSTRAINT `AIUsage_userId_fkey` FOREIGN KEY (`userId`) REFERENCES `UserCredential`(`userId`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `Chat` ADD CONSTRAINT `Chat_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `ChatMessage` ADD CONSTRAINT `ChatMessage_chatId_fkey` FOREIGN KEY (`chatId`) REFERENCES `Chat`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentGroup` ADD CONSTRAINT `AssignmentGroup_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentGroup` ADD CONSTRAINT `AssignmentGroup_groupId_fkey` FOREIGN KEY (`groupId`) REFERENCES `Group`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `Assignment` ADD CONSTRAINT `Assignment_currentVersionId_fkey` FOREIGN KEY (`currentVersionId`) REFERENCES `AssignmentVersion`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentFile` ADD CONSTRAINT `AssignmentFile_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentVersion` ADD CONSTRAINT `AssignmentVersion_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `QuestionVersion` ADD CONSTRAINT `QuestionVersion_assignmentVersionId_fkey` FOREIGN KEY (`assignmentVersionId`) REFERENCES `AssignmentVersion`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `VersionHistory` ADD CONSTRAINT `VersionHistory_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `VersionHistory` ADD CONSTRAINT `VersionHistory_fromVersionId_fkey` FOREIGN KEY (`fromVersionId`) REFERENCES `AssignmentVersion`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `VersionHistory` ADD CONSTRAINT `VersionHistory_toVersionId_fkey` FOREIGN KEY (`toVersionId`) REFERENCES `AssignmentVersion`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `Question` ADD CONSTRAINT `Question_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentAttempt` ADD CONSTRAINT `AssignmentAttempt_assignmentVersionId_fkey` FOREIGN KEY (`assignmentVersionId`) REFERENCES `AssignmentVersion`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `GradingProgress` ADD CONSTRAINT `GradingProgress_attemptId_fkey` FOREIGN KEY (`attemptId`) REFERENCES `AssignmentAttempt`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentAttemptQuestionVariant` ADD CONSTRAINT `AssignmentAttemptQuestionVariant_assignmentAttemptId_fkey` FOREIGN KEY (`assignmentAttemptId`) REFERENCES `AssignmentAttempt`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentAttemptQuestionVariant` ADD CONSTRAINT `AssignmentAttemptQuestionVariant_questionVariantId_fkey` FOREIGN KEY (`questionVariantId`) REFERENCES `QuestionVariant`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentAttemptQuestionVariant` ADD CONSTRAINT `AssignmentAttemptQuestionVariant_questionId_fkey` FOREIGN KEY (`questionId`) REFERENCES `Question`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `QuestionVariant` ADD CONSTRAINT `QuestionVariant_questionId_fkey` FOREIGN KEY (`questionId`) REFERENCES `Question`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `QuestionResponse` ADD CONSTRAINT `QuestionResponse_assignmentAttemptId_fkey` FOREIGN KEY (`assignmentAttemptId`) REFERENCES `AssignmentAttempt`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentFeedback` ADD CONSTRAINT `AssignmentFeedback_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentFeedback` ADD CONSTRAINT `AssignmentFeedback_attemptId_fkey` FOREIGN KEY (`attemptId`) REFERENCES `AssignmentAttempt`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `RegradingRequest` ADD CONSTRAINT `RegradingRequest_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `RegradingRequest` ADD CONSTRAINT `RegradingRequest_attemptId_fkey` FOREIGN KEY (`attemptId`) REFERENCES `AssignmentAttempt`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `Report` ADD CONSTRAINT `Report_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `Report` ADD CONSTRAINT `Report_duplicateOfReportId_fkey` FOREIGN KEY (`duplicateOfReportId`) REFERENCES `Report`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `Report` ADD CONSTRAINT `Report_relatedToReportId_fkey` FOREIGN KEY (`relatedToReportId`) REFERENCES `Report`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `ReportDiagnostics` ADD CONSTRAINT `ReportDiagnostics_reportId_fkey` FOREIGN KEY (`reportId`) REFERENCES `Report`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `LtiGradeSync` ADD CONSTRAINT `LtiGradeSync_attemptId_fkey` FOREIGN KEY (`attemptId`) REFERENCES `AssignmentAttempt`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `LtiSyncErrorLog` ADD CONSTRAINT `LtiSyncErrorLog_syncId_fkey` FOREIGN KEY (`syncId`) REFERENCES `LtiGradeSync`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `Translation` ADD CONSTRAINT `Translation_questionId_fkey` FOREIGN KEY (`questionId`) REFERENCES `Question`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `Translation` ADD CONSTRAINT `Translation_variantId_fkey` FOREIGN KEY (`variantId`) REFERENCES `QuestionVariant`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentDraft` ADD CONSTRAINT `AssignmentDraft_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentTranslation` ADD CONSTRAINT `AssignmentTranslation_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `FeedbackTranslation` ADD CONSTRAINT `FeedbackTranslation_questionId_fkey` FOREIGN KEY (`questionId`) REFERENCES `Question`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentAuthor` ADD CONSTRAINT `AssignmentAuthor_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `LLMPricing` ADD CONSTRAINT `LLMPricing_modelId_fkey` FOREIGN KEY (`modelId`) REFERENCES `LLMModel`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `LLMFeatureAssignment` ADD CONSTRAINT `LLMFeatureAssignment_featureId_fkey` FOREIGN KEY (`featureId`) REFERENCES `AIFeature`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `LLMFeatureAssignment` ADD CONSTRAINT `LLMFeatureAssignment_modelId_fkey` FOREIGN KEY (`modelId`) REFERENCES `LLMModel`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `ClassTeacher` ADD CONSTRAINT `ClassTeacher_classId_fkey` FOREIGN KEY (`classId`) REFERENCES `Class`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `ClassEnrollment` ADD CONSTRAINT `ClassEnrollment_classId_fkey` FOREIGN KEY (`classId`) REFERENCES `Class`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentClass` ADD CONSTRAINT `AssignmentClass_assignmentId_fkey` FOREIGN KEY (`assignmentId`) REFERENCES `Assignment`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `AssignmentClass` ADD CONSTRAINT `AssignmentClass_classId_fkey` FOREIGN KEY (`classId`) REFERENCES `Class`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `KnowledgePoint` ADD CONSTRAINT `KnowledgePoint_parentId_fkey` FOREIGN KEY (`parentId`) REFERENCES `KnowledgePoint`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `QuestionKnowledgePoint` ADD CONSTRAINT `QuestionKnowledgePoint_questionId_fkey` FOREIGN KEY (`questionId`) REFERENCES `Question`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `QuestionKnowledgePoint` ADD CONSTRAINT `QuestionKnowledgePoint_knowledgePointId_fkey` FOREIGN KEY (`knowledgePointId`) REFERENCES `KnowledgePoint`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `_ChatToUserCredential` ADD CONSTRAINT `_ChatToUserCredential_A_fkey` FOREIGN KEY (`A`) REFERENCES `Chat`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `_ChatToUserCredential` ADD CONSTRAINT `_ChatToUserCredential_B_fkey` FOREIGN KEY (`B`) REFERENCES `UserCredential`(`userId`) ON DELETE CASCADE ON UPDATE CASCADE;
