package com.mentorhub.controller;

import com.mentorhub.dto.*;
import com.mentorhub.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    public ResponseEntity<List<StudentDisciplineResponse>> getStudents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "true") Boolean active
    ) {
        List<StudentDisciplineResponse> students = studentService.getStudents(search, active);
        return ResponseEntity.ok(students);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentDisciplineResponse> getStudentById(@PathVariable String id) {
        Long studentId = parseId(id);
        StudentDisciplineResponse student = studentService.getStudentById(studentId);
        return ResponseEntity.ok(student);
    }

    @PostMapping
    public ResponseEntity<StudentDisciplineResponse> createStudent(@Valid @RequestBody CreateStudentRequest request) {
        StudentDisciplineResponse created = studentService.createStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StudentDisciplineResponse> updateStudent(
            @PathVariable String id,
            @RequestBody UpdateStudentRequest request
    ) {
        Long studentId = parseId(id);
        StudentDisciplineResponse updated = studentService.updateStudent(studentId, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable String id) {
        Long studentId = parseId(id);
        studentService.deleteStudent(studentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/toggle-pause")
    public ResponseEntity<TogglePauseResponse> togglePause(
            @PathVariable String id,
            @Valid @RequestBody TogglePauseRequest request
    ) {
        Long studentId = parseId(id);
        TogglePauseResponse response = studentService.togglePause(studentId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/discipline-calendar")
    public ResponseEntity<List<DayInfoResponse>> getDisciplineCalendar(@PathVariable String id) {
        Long studentId = parseId(id);
        List<DayInfoResponse> calendar = studentService.getDisciplineCalendar(studentId);
        return ResponseEntity.ok(calendar);
    }

    private Long parseId(String idStr) {
        if (idStr == null) return null;
        String clean = idStr.trim();
        if (clean.startsWith("st-")) {
            clean = clean.substring(3);
        }
        return Long.parseLong(clean);
    }
}
