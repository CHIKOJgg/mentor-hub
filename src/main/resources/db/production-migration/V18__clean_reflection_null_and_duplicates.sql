-- V18: Clean up stringified 'null' questions and bad reflections
UPDATE daily_reports 
SET reflection_question = NULL 
WHERE reflection_question ILIKE 'null' 
   OR reflection_question ILIKE 'none'
   OR reflection_question ILIKE 'undefined'
   OR reflection_question ILIKE 'нет'
   OR reflection_question ILIKE 'n/a';

UPDATE daily_reports
SET mentor_question = NULL
WHERE mentor_question ILIKE '%null%' 
   OR mentor_question ILIKE '%undefined%';
