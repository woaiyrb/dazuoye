package com.zuoye.mark.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeacherWorkflowMapper {

    @Select("""
            SELECT h.status
            FROM course_homework h
            JOIN classroom c ON c.id = h.classId
            WHERE h.id = #{homeworkId} AND c.teacherId = #{teacherId}
            """)
    String selectHomeworkStatus(
            @Param("teacherId") int teacherId,
            @Param("homeworkId") int homeworkId);

    @Select("""
            SELECT id, content, status, model,
                   promptVersion, generationNo, elapsedMs,
                   errorCode, evidence, createdAt
            FROM ai_comment_draft
            WHERE homeworkId = #{homeworkId}
              AND questionId = #{questionId}
            ORDER BY generationNo DESC, id DESC
            LIMIT 1
            """)
    Map<String, Object> selectLatestAiDraft(
            @Param("homeworkId") int homeworkId,
            @Param("questionId") int questionId);
}
