package org.motechproject.scheduletracking.api.service.impl;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.motechproject.model.Time;
import org.motechproject.scheduletracking.api.domain.*;
import org.motechproject.scheduletracking.api.domain.exception.NoMoreMilestonesToFulfillException;
import org.motechproject.scheduletracking.api.repository.AllEnrollments;
import org.motechproject.scheduletracking.api.repository.AllTrackedSchedules;
import org.motechproject.util.DateUtil;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.motechproject.scheduletracking.api.domain.EnrollmentStatus.Active;
import static org.motechproject.scheduletracking.api.utility.DateTimeUtil.weeksAgo;
import static org.motechproject.scheduletracking.api.utility.PeriodFactory.days;
import static org.motechproject.scheduletracking.api.utility.PeriodFactory.weeks;
import static org.motechproject.util.DateUtil.now;

public class EnrollmentServiceTest {
    private EnrollmentService enrollmentService;

    @Mock
    private AllTrackedSchedules allTrackedSchedules;
    @Mock
    private AllEnrollments allEnrollments;
    @Mock
    private EnrollmentAlertService enrollmentAlertService;
    @Mock
    private EnrollmentDefaultmentService enrollmentDefaultmentService;

    @Before
    public void setup() {
        initMocks(this);
        enrollmentService = new EnrollmentService(allTrackedSchedules, allEnrollments, enrollmentAlertService, enrollmentDefaultmentService);
    }

    @Test
    public void shouldEnrollEntityIntoSchedule() {
        String externalId = "entity_1";
        String scheduleName = "my_schedule";
        DateTime referenceDate = weeksAgo(0);
        DateTime enrollmentDate = weeksAgo(0);
        Time preferredAlertTime = new Time(8, 10);

        Milestone milestone = new Milestone("milestone", weeks(1), weeks(1), weeks(1), weeks(1));
        milestone.addAlert(WindowName.earliest, new Alert(days(0), days(1), 3, 0));
        milestone.addAlert(WindowName.due, new Alert(days(0), weeks(1), 2, 1));
        Schedule schedule = new Schedule(scheduleName);
        schedule.addMilestones(milestone);
        when(allTrackedSchedules.getByName(scheduleName)).thenReturn(schedule);
        Enrollment dummyEnrollment = new Enrollment(externalId, scheduleName, milestone.getName(), referenceDate, enrollmentDate, preferredAlertTime, Active);
        dummyEnrollment.setId("enrollmentId");
        when(allEnrollments.addOrReplace(any(Enrollment.class))).thenReturn(dummyEnrollment);

        enrollmentService.enroll(externalId, scheduleName, milestone.getName(), referenceDate, enrollmentDate, preferredAlertTime);

        ArgumentCaptor<Enrollment> enrollmentArgumentCaptor = ArgumentCaptor.forClass(Enrollment.class);
        verify(allEnrollments).addOrReplace(enrollmentArgumentCaptor.capture());
        assertEnrollment(enrollmentArgumentCaptor.getValue(), externalId, scheduleName, milestone, Active);

        verify(enrollmentAlertService).scheduleAlertsForCurrentMilestone(enrollmentArgumentCaptor.capture());
        assertEnrollment(enrollmentArgumentCaptor.getValue(), externalId, scheduleName, milestone, Active);

        verify(enrollmentDefaultmentService).scheduleJobToCaptureDefaultment(enrollmentArgumentCaptor.getValue());
        assertEnrollment(enrollmentArgumentCaptor.getValue(), externalId, scheduleName, milestone, Active);
    }

    @Test
    public void shouldEnrollEntityAsDefaultedOneIfScheduleDurationHasExpired() {
        String externalId = "entity_1";
        String scheduleName = "my_schedule";
        DateTime referenceDateTime = DateUtil.now().minusDays(29);
        DateTime enrollmentDateTime = DateUtil.now();
        Time preferredAlertTime = new Time(8, 10);
        EnrollmentStatus enrollmentStatus = EnrollmentStatus.Defaulted;

        Milestone milestone = new Milestone("milestone", weeks(1), weeks(1), weeks(1), weeks(1));
        Schedule schedule = new Schedule(scheduleName);
        schedule.addMilestones(milestone);
        when(allTrackedSchedules.getByName(scheduleName)).thenReturn(schedule);
        Enrollment dummyEnrollment = new Enrollment(externalId, scheduleName, milestone.getName(), referenceDateTime, enrollmentDateTime, preferredAlertTime, EnrollmentStatus.Active);
        dummyEnrollment.setId("enrollmentId");
        when(allEnrollments.addOrReplace(any(Enrollment.class))).thenReturn(dummyEnrollment);

        enrollmentService.enroll(externalId, scheduleName, milestone.getName(), referenceDateTime, enrollmentDateTime, preferredAlertTime);

        ArgumentCaptor<Enrollment> enrollmentArgumentCaptor = ArgumentCaptor.forClass(Enrollment.class);
        verify(allEnrollments).addOrReplace(enrollmentArgumentCaptor.capture());
        assertEnrollment(enrollmentArgumentCaptor.getValue(), externalId, scheduleName, milestone, enrollmentStatus);
    }

    private void assertEnrollment(Enrollment enrollment, String externalId, String scheduleName, Milestone milestone, EnrollmentStatus enrollmentStatus) {
        assertEquals(externalId, enrollment.getExternalId());
        assertEquals(scheduleName, enrollment.getScheduleName());
        assertEquals(milestone.getName(), enrollment.getCurrentMilestoneName());
        assertEquals(enrollmentStatus, enrollment.getStatus());
    }

    @Test
    public void shouldFulfillCurrentMilestoneInEnrollment() {
        Milestone firstMilestone = new Milestone("First Shot", weeks(1), weeks(1), weeks(1), weeks(1));
        Milestone secondMilestone = new Milestone("Second Shot", weeks(1), weeks(1), weeks(1), weeks(1));
        secondMilestone.addAlert(WindowName.earliest, new Alert(days(0), weeks(1), 3, 0));
        Schedule schedule = new Schedule("Yellow Fever Vaccination");
        schedule.addMilestones(firstMilestone, secondMilestone);
        when(allTrackedSchedules.getByName("Yellow Fever Vaccination")).thenReturn(schedule);

        Enrollment enrollment = new Enrollment("ID-074285", "Yellow Fever Vaccination", "First Shot", weeksAgo(4), weeksAgo(4), new Time(8, 20), Active);
        DateTime now = now();
        enrollmentService.fulfillCurrentMilestone(enrollment, now);

        assertEquals("Second Shot", enrollment.getCurrentMilestoneName());
        assertEquals(now, enrollment.lastFulfilledDate());

        verify(allEnrollments).update(enrollment);
    }

    @Test
    public void shouldScheduleJobsForNextMilestoneWhenCurrentMilestoneIsFulfilled() {
        Milestone firstMilestone = new Milestone("First Shot", weeks(1), weeks(1), weeks(1), weeks(1));
        Milestone secondMilestone = new Milestone("Second Shot", weeks(1), weeks(1), weeks(1), weeks(1));
        secondMilestone.addAlert(WindowName.earliest, new Alert(days(0), weeks(1), 3, 0));
        Schedule schedule = new Schedule("Yellow Fever Vaccination");
        schedule.addMilestones(firstMilestone, secondMilestone);
        when(allTrackedSchedules.getByName("Yellow Fever Vaccination")).thenReturn(schedule);

        Enrollment enrollment = new Enrollment("ID-074285", "Yellow Fever Vaccination", "First Shot", weeksAgo(4), weeksAgo(4), new Time(8, 20), Active);
        enrollment.setId("enrollment_1");
        enrollmentService.fulfillCurrentMilestone(enrollment, null);

        verify(enrollmentAlertService).unscheduleAllAlerts(enrollment);
        verify(enrollmentDefaultmentService).unscheduleDefaultmentCaptureJob(enrollment);

        ArgumentCaptor<Enrollment> updatedEnrollmentCaptor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentAlertService).scheduleAlertsForCurrentMilestone(updatedEnrollmentCaptor.capture());
        assertEquals("Second Shot", updatedEnrollmentCaptor.getValue().getCurrentMilestoneName());

        verify(enrollmentDefaultmentService).scheduleJobToCaptureDefaultment(updatedEnrollmentCaptor.capture());
        assertEquals("Second Shot", updatedEnrollmentCaptor.getValue().getCurrentMilestoneName());

        verify(allEnrollments).update(enrollment);
    }

    @Test(expected = NoMoreMilestonesToFulfillException.class)
    public void shouldThrowExceptionIfAllMilestonesAreFulfilled() {
        Milestone firstMilestone = new Milestone("First Shot", weeks(1), weeks(1), weeks(1), weeks(1));
        Milestone secondMilestone = new Milestone("Second Shot", weeks(1), weeks(1), weeks(1), weeks(1));
        Schedule schedule = new Schedule("Yellow Fever Vaccination");
        schedule.addMilestones(firstMilestone, secondMilestone);
        when(allTrackedSchedules.getByName("Yellow Fever Vaccination")).thenReturn(schedule);

        Enrollment enrollment = new Enrollment("ID-074285", "Yellow Fever Vaccination", "First Shot", weeksAgo(4), weeksAgo(4), new Time(8, 20), Active);
        enrollmentService.fulfillCurrentMilestone(enrollment, null);
        enrollmentService.fulfillCurrentMilestone(enrollment, null);
        enrollmentService.fulfillCurrentMilestone(enrollment, null);

        assertEquals(null, enrollment.getCurrentMilestoneName());
    }

    @Test
    public void shouldCompleteEnrollmentWhenAllMilestonesAreFulfilled() {
        Milestone firstMilestone = new Milestone("First Shot", weeks(1), weeks(1), weeks(1), weeks(1));
        Milestone secondMilestone = new Milestone("Second Shot", weeks(1), weeks(1), weeks(1), weeks(1));
        String scheduleName = "Yellow Fever Vaccination";
        Schedule schedule = new Schedule(scheduleName);
        schedule.addMilestones(firstMilestone, secondMilestone);
        when(allTrackedSchedules.getByName(scheduleName)).thenReturn(schedule);

        Enrollment enrollment = new Enrollment("ID-074285", scheduleName, "First Shot", weeksAgo(4), weeksAgo(4), new Time(8, 20), Active);
        enrollmentService.fulfillCurrentMilestone(enrollment, null);
        enrollmentService.fulfillCurrentMilestone(enrollment, null);

        assertEquals(true, enrollment.isCompleted());
        verify(allEnrollments, times(2)).update(enrollment);
    }

    @Test
    public void shouldNotHaveAnyJobsScheduledAfterEnrollmentIsComplete() {
        Milestone firstMilestone = new Milestone("First Shot", weeks(1), weeks(1), weeks(1), weeks(1));
        Schedule schedule = new Schedule("Yellow Fever Vaccination");
        schedule.addMilestones(firstMilestone);
        when(allTrackedSchedules.getByName("Yellow Fever Vaccination")).thenReturn(schedule);

        Enrollment enrollment = new Enrollment("ID-074285", "Yellow Fever Vaccination", "First Shot", weeksAgo(4), weeksAgo(4), new Time(8, 20), Active);
        enrollmentService.fulfillCurrentMilestone(enrollment, null);

        verify(enrollmentAlertService).unscheduleAllAlerts(enrollment);
        verify(enrollmentDefaultmentService).unscheduleDefaultmentCaptureJob(enrollment);
        verify(enrollmentAlertService, times(0)).scheduleAlertsForCurrentMilestone(enrollment);
        verify(enrollmentDefaultmentService, times(0)).scheduleJobToCaptureDefaultment(enrollment);
    }

    @Test
    public void shouldUnenrollEntityFromTheSchedule() {
        Milestone milestone = new Milestone("milestone", weeks(1), weeks(1), weeks(1), weeks(1));
        milestone.addAlert(WindowName.earliest, new Alert(days(0), days(1), 3, 0));
        String scheduleName = "my_schedule";
        Schedule schedule = new Schedule(scheduleName);
        schedule.addMilestones(milestone);
        when(allTrackedSchedules.getByName(scheduleName)).thenReturn(schedule);

        Enrollment enrollment = new Enrollment("entity_1", scheduleName, "milestone", weeksAgo(4), weeksAgo(4), new Time(8, 10), Active);
        enrollment.setId("enrollment_1");
        enrollmentService.unenroll(enrollment);

        ArgumentCaptor<Enrollment> enrollmentCaptor = ArgumentCaptor.forClass(Enrollment.class);
        verify(allEnrollments).update(enrollmentCaptor.capture());

        enrollment = enrollmentCaptor.getValue();
        Assert.assertEquals("entity_1", enrollment.getExternalId());
        Assert.assertEquals(scheduleName, enrollment.getScheduleName());
        assertEquals(false, enrollment.isActive());

        verify(enrollmentAlertService).unscheduleAllAlerts(enrollment);
        verify(enrollmentDefaultmentService).unscheduleDefaultmentCaptureJob(enrollment);
    }

    @Test
    public void shouldReturnReferenceDateWhenCurrentMilestoneIsTheFirstMilestone() {
        Milestone milestone = new Milestone("milestone", weeks(1), weeks(1), weeks(1), weeks(1));
        Schedule schedule = new Schedule("my_schedule");
        schedule.addMilestones(milestone);
        when(allTrackedSchedules.getByName("my_schedule")).thenReturn(schedule);

        Enrollment enrollment = new Enrollment("ID-074285", "my_schedule", "milestone", weeksAgo(5), weeksAgo(3), new Time(8, 20), EnrollmentStatus.Active);
        assertEquals(weeksAgo(5), enrollmentService.getCurrentMilestoneStartDate(enrollment));
    }

    @Test
    public void shouldReturnEnrollmentDateWhenEnrolledIntoSecondMilestoneAndNoMilestonesFulfilled() {
        Milestone firstMilestone = new Milestone("first_milestone", weeks(1), weeks(1), weeks(1), weeks(1));
        Milestone secondMilestone = new Milestone("second_milestone", weeks(1), weeks(1), weeks(1), weeks(1));
        Schedule schedule = new Schedule("my_schedule");
        schedule.addMilestones(firstMilestone, secondMilestone);
        when(allTrackedSchedules.getByName("my_schedule")).thenReturn(schedule);

        Enrollment enrollment = new Enrollment("ID-074285", "my_schedule", "second_milestone", weeksAgo(5), weeksAgo(3), null, EnrollmentStatus.Active);
        assertEquals(weeksAgo(3), enrollmentService.getCurrentMilestoneStartDate(enrollment));
    }

    @Test
    public void shouldReturnReferenceDateAsTheMilestoneStartDateOfTheAnyMilestoneWhenTheScheduleIsBasedOnAbsoluteWindows() {
        Milestone firstMilestone = new Milestone("First Milestone", weeks(1), weeks(1), weeks(1), weeks(1));
        Milestone secondMilestone = new Milestone("Second Milestone", weeks(1), weeks(1), weeks(1), weeks(1));
        Schedule schedule = new Schedule("my_schedule").isBasedOnAbsoluteWindows(true);
        schedule.addMilestones(firstMilestone, secondMilestone);
        when(allTrackedSchedules.getByName("my_schedule")).thenReturn(schedule);

        DateTime referenceDateTime = weeksAgo(5);

        Enrollment enrollment = new Enrollment("ID-074285", "my_schedule", "First Milestone", referenceDateTime, weeksAgo(3), null, EnrollmentStatus.Active);
        assertEquals(referenceDateTime, enrollmentService.getCurrentMilestoneStartDate(enrollment));

        Enrollment enrollmentIntoSecondMilestone = new Enrollment("ID-074285", "my_schedule", "Second Milestone", referenceDateTime, weeksAgo(3), null, EnrollmentStatus.Active);
        assertEquals(referenceDateTime, enrollmentService.getCurrentMilestoneStartDate(enrollmentIntoSecondMilestone));
    }


}
