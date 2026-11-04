USE `mark`;

SET @add_instructions = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `course_homework` ADD COLUMN `instructions` TEXT NULL AFTER `description`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'course_homework'
    AND column_name = 'instructions'
);
PREPARE add_instructions_stmt FROM @add_instructions;
EXECUTE add_instructions_stmt;
DEALLOCATE PREPARE add_instructions_stmt;

SET @add_time_estimate = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `course_homework` ADD COLUMN `timeEstimateMinutes` INT NOT NULL DEFAULT 10 AFTER `instructions`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'course_homework'
    AND column_name = 'timeEstimateMinutes'
);
PREPARE add_time_estimate_stmt FROM @add_time_estimate;
EXECUTE add_time_estimate_stmt;
DEALLOCATE PREPARE add_time_estimate_stmt;
