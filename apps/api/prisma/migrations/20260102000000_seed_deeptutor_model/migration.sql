-- Register DeepTutor as the only AI model and point every AI feature at it.
-- Safe to re-run: every statement is idempotent.

INSERT INTO `LLMModel` (`modelKey`, `displayName`, `provider`, `isActive`, `createdAt`, `updatedAt`)
VALUES ('deeptutor', 'DeepTutor', 'deeptutor', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  `displayName` = 'DeepTutor',
  `provider` = 'deeptutor',
  `isActive` = 1;

INSERT INTO `AIFeature` (`featureKey`, `featureType`, `displayName`, `description`, `isActive`, `requiresModel`, `defaultModelKey`, `createdAt`, `updatedAt`) VALUES
  ('text_grading',          'TEXT_GRADING',         'Text grading',          'AI grading of short text answers',          1, 1, 'deeptutor', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('file_grading',          'FILE_GRADING',         'File grading',          'AI grading of uploaded files',              1, 1, 'deeptutor', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('image_grading',         'IMAGE_GRADING',        'Image grading',         'AI grading of image submissions',           1, 1, 'deeptutor', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('url_grading',           'URL_GRADING',          'URL grading',           'AI grading of URL submissions',             1, 1, 'deeptutor', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('presentation_grading',  'PRESENTATION_GRADING', 'Presentation grading',  'AI grading of presentation decks',          1, 1, 'deeptutor', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('video_grading',         'VIDEO_GRADING',        'Video grading',         'AI grading of video presentations',         1, 1, 'deeptutor', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('question_generation',   'QUESTION_GENERATION',  'Question generation',   'AI authoring of new questions',             1, 1, 'deeptutor', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('translation',           'TRANSLATION',          'Translation',           'AI translation of assignment content',      1, 1, 'deeptutor', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('rubric_generation',     'RUBRIC_GENERATION',    'Rubric generation',     'AI authoring of grading rubrics',           1, 1, 'deeptutor', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('content_moderation',    'CONTENT_MODERATION',   'Content moderation',    'AI safety check on authored content',       1, 1, 'deeptutor', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `defaultModelKey` = 'deeptutor';

INSERT INTO `LLMFeatureAssignment` (`featureId`, `modelId`, `isActive`, `priority`, `assignedBy`, `assignedAt`)
SELECT f.`id`, m.`id`, 1, 1, 'system', CURRENT_TIMESTAMP(3)
FROM `AIFeature` f
JOIN `LLMModel` m ON m.`modelKey` = 'deeptutor'
WHERE NOT EXISTS (
  SELECT 1 FROM `LLMFeatureAssignment` a
  WHERE a.`featureId` = f.`id` AND a.`modelId` = m.`id`
);
