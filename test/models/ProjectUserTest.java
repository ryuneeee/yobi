package models;

import java.util.List;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;

public class ProjectUserTest extends ModelTest<ProjectUser> {
    @Test
    public void findByIds() throws Exception {
        // Given
        // When
        Role role = ProjectUser.findByIds(2l, 1l).role;
        // Then
        assertThat(role.id).isEqualTo(1l);
    }

    @Test
    public void create() throws Exception {
        // Given
        // When
        ProjectUser.create(2l, 3l, 2l);
        // Then
        assertThat(ProjectUser.findByIds(2l, 3l).role.id)
                .isEqualTo(2l);
    }

    @Test
    public void assignRole() throws Exception {
        // Given
        // When
        ProjectUser.assignRole(2l, 1l, 2l);
        ProjectUser.assignRole(2l, 3l, 2l);
        flush();
        // Then
        assertThat(ProjectUser.findByIds(2l, 1l).role.id).isEqualTo(2l);
        assertThat(ProjectUser.findByIds(2l, 3l).role.id).isEqualTo(2l);
    }

    @Test
    public void isManager() throws Exception {
        // Given
        ProjectUser.assignRole(2l, 3l, 1l);
        flush();

        // When
        Long userIdCase1 = 2l;
        Long userIdCase2 = 5l;
        
        // Then
        assertThat(ProjectUser.checkOneMangerPerOneProject(userIdCase1, 3l)).isEqualTo(true);
        assertThat(ProjectUser.checkOneMangerPerOneProject(userIdCase2, 3l)).isEqualTo(false);
    }

    @Test
    public void isMember() throws Exception {
        // Given
        // When
        // Then
        assertThat(ProjectUser.isMember(2l, 2l)).isEqualTo(true);
        assertThat(ProjectUser.isMember(2l, 3l)).isEqualTo(false);
    }

    @Test
    public void options() throws Exception {
        // Given
        // When
        // Then
        assertThat(ProjectUser.options(1l).containsValue("laziel")).isEqualTo(true);
    }

    @Test
    public void findMemberListByProject() throws Exception {
        // Given
        // When
        List<ProjectUser> projectUsers = ProjectUser.findMemberListByProject(1l);
        // Then
        assertThat(projectUsers.size()).isEqualTo(2);
        assertThat(projectUsers.get(0).user.id).isEqualTo(2l);
        assertThat(projectUsers.get(0).user.loginId).isEqualTo("yobi");
        assertThat(projectUsers.get(0).role.name).isEqualTo("manager");
    }

    @Test
    public void roleOf() {
        // GIVEN
        String loginId = "yobi";
        Project project = Project.findByOwnerAndProjectName(loginId, "projectYobi");
        // WHEN
        String roleName = ProjectUser.roleOf(loginId, project);
        // THEN
        assertThat(roleName).isEqualTo("manager");

        // WHEN
        roleName = ProjectUser.roleOf("admin", project);
        // THEN
        assertThat(roleName).isEqualTo("sitemanager");

        // WHEN
        roleName = ProjectUser.roleOf(null, project);
        // THEN
        assertThat(roleName).isEqualTo("anonymous");

        // WHEN
        roleName = ProjectUser.roleOf("keesun", project);
        // THEN
        assertThat(roleName).isEqualTo("anonymous");

        // WHEN
        roleName = ProjectUser.roleOf("laziel", project);
        // THEN
        assertThat(roleName).isEqualTo("member");
    }

    @Test
    public void isAllowedToSettings() {
        // GIVEN
        String loginId = "yobi";
        Project project = Project.findByOwnerAndProjectName(loginId, "projectYobi");
        // WHEN // THEN
        assertThat(ProjectUser.isAllowedToSettings(loginId, project)).isTrue();
        // WHEN // THEN
        assertThat(ProjectUser.isAllowedToSettings("admin", project)).isTrue();
        // WHEN // THEN
        assertThat(ProjectUser.isAllowedToSettings(null, project)).isFalse();
        // WHEN // THEN
        assertThat(ProjectUser.isAllowedToSettings("keesun", project)).isFalse();

    }
}
