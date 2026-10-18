package com.pydlove.regent.testing.application.query;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.repository.TestCaseRepository;
import com.pydlove.regent.testing.domain.repository.TestCaseRunRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 测试用例查询。 */
public class TestCaseQueryService {

    private final TestCaseRepository testCaseRepository;
    private final TestCaseRunRepository testCaseRunRepository;
    private final ProjectProfileRepository projectProfileRepository;

    public TestCaseQueryService(TestCaseRepository testCaseRepository,
                                TestCaseRunRepository testCaseRunRepository,
                                ProjectProfileRepository projectProfileRepository) {
        this.testCaseRepository = testCaseRepository;
        this.testCaseRunRepository = testCaseRunRepository;
        this.projectProfileRepository = projectProfileRepository;
    }

    public List<TestCaseOverview> list(Long projectId) {
        List<TestCase> cases = projectId == null
                ? testCaseRepository.findAll()
                : testCaseRepository.findByProject(ProjectId.of(projectId));
        if (cases.isEmpty()) {
            return List.of();
        }

        Map<Long, String> projectNames = projectProfileRepository.findAll().stream()
                .collect(Collectors.toMap(
                        project -> project.id().value(),
                        ProjectProfile::name,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<TestCaseId, TestCaseRun> latest = testCaseRunRepository
                .findLatestByTestCaseIds(cases.stream().map(TestCase::getId).toList())
                .stream()
                .collect(Collectors.toMap(TestCaseRun::getTestCaseId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        return cases.stream()
                .map(testCase -> new TestCaseOverview(
                        testCase,
                        projectNames.getOrDefault(testCase.getProjectId().value(),
                                "项目 #" + testCase.getProjectId().value()),
                        latest.get(testCase.getId())))
                .toList();
    }

    public TestCase get(TestCaseId id) {
        return testCaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("测试用例不存在：" + id.value()));
    }

    public TestCaseOverview getOverview(TestCaseId id) {
        TestCase testCase = get(id);
        String projectName = projectProfileRepository.findById(testCase.getProjectId())
                .map(ProjectProfile::name)
                .orElse("项目 #" + testCase.getProjectId().value());
        TestCaseRun latest = testCaseRunRepository
                .findLatestByTestCaseIds(List.of(id))
                .stream()
                .findFirst()
                .orElse(null);
        return new TestCaseOverview(testCase, projectName, latest);
    }
}
