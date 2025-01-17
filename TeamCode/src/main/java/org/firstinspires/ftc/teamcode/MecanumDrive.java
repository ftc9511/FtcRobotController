package org.firstinspires.ftc.teamcode;

import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.roadrunner.AccelConstraint;
import com.acmerobotics.roadrunner.AngularVelConstraint;
import com.acmerobotics.roadrunner.DualNum;
import com.acmerobotics.roadrunner.HolonomicController;
import com.acmerobotics.roadrunner.MecanumKinematics;
import com.acmerobotics.roadrunner.MinVelConstraint;
import com.acmerobotics.roadrunner.MotorFeedforward;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.Pose2dDual;
import com.acmerobotics.roadrunner.ProfileAccelConstraint;
import com.acmerobotics.roadrunner.Profiles;
import com.acmerobotics.roadrunner.Rotation2d;
import com.acmerobotics.roadrunner.Rotation2dDual;
import com.acmerobotics.roadrunner.SafeTrajectoryBuilder;
import com.acmerobotics.roadrunner.Time;
import com.acmerobotics.roadrunner.TimeProfile;
import com.acmerobotics.roadrunner.TimeTrajectory;
import com.acmerobotics.roadrunner.Trajectory;
import com.acmerobotics.roadrunner.TrajectoryBuilder;
import com.acmerobotics.roadrunner.Twist2d;
import com.acmerobotics.roadrunner.Twist2dDual;
import com.acmerobotics.roadrunner.Twist2dIncrDual;
import com.acmerobotics.roadrunner.Vector2d;
import com.acmerobotics.roadrunner.VelConstraint;
import com.qualcomm.hardware.bosch.BNO055IMU;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.VoltageSensor;

import org.firstinspires.ftc.teamcode.util.BNO055Wrapper;
import org.firstinspires.ftc.teamcode.util.Encoder;
import org.firstinspires.ftc.teamcode.util.Localizer;
import org.firstinspires.ftc.teamcode.util.LynxFirmwareVersion;
import org.firstinspires.ftc.teamcode.util.OverflowEncoder;
import org.firstinspires.ftc.teamcode.util.RawEncoder;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@Config
public final class MecanumDrive {
    //todo configure and tune all the parameters
    // drive model parameters
    public static double IN_PER_TICK = (Math.PI * 2.0 * 1.88976) / 537.7;
    public static double LATERAL_IN_PER_TICK = 1;
    public static double TRACK_WIDTH_TICKS = 0;

    // feedforward parameters
    public static double kS = 0;
    public static double kV = 0;
    public static double kA = 0;

    // path profile parameters
    public static double MAX_WHEEL_VEL = 50;
    public static double MIN_PROFILE_ACCEL = -30;
    public static double MAX_PROFILE_ACCEL = 50;

    // turn profile parameters
    public static double MAX_ANG_VEL = Math.PI; // shared with path
    public static double MAX_ANG_ACCEL = Math.PI;

    // path controller gains
    public static double AXIAL_GAIN = 0.0;
    public static double LATERAL_GAIN = 0.0;
    public static double HEADING_GAIN = 0.0; // shared with turn

    public static double AXIAL_VEL_GAIN = 0.0;
    public static double LATERAL_VEL_GAIN = 0.0;
    public static double HEADING_VEL_GAIN = 0.0; // shared with turn

    public final MecanumKinematics kinematics = new MecanumKinematics(
            IN_PER_TICK * TRACK_WIDTH_TICKS,
            IN_PER_TICK / LATERAL_IN_PER_TICK);

    public final MotorFeedforward feedforward = new MotorFeedforward(kS, kV, kA);

    public final VelConstraint defaultVelConstraint =
            new MinVelConstraint(Arrays.asList(
                    kinematics.new WheelVelConstraint(MAX_WHEEL_VEL),
                    new AngularVelConstraint(MAX_ANG_VEL)
            ));
    public final AccelConstraint defaultAccelConstraint =
            new ProfileAccelConstraint(MIN_PROFILE_ACCEL, MAX_PROFILE_ACCEL);

    public final DcMotorEx leftFront, leftBack, rightBack, rightFront;

    public final VoltageSensor voltageSensor;

    public final BNO055Wrapper imu;

    public final Localizer localizer;
    public Pose2d pose;

    public final double inPerTick = IN_PER_TICK;

    private final LinkedList<Pose2d> poseHistory = new LinkedList<>();

    public class DriveLocalizer implements Localizer {
        public final Encoder leftFront, leftRear, rightRear, rightFront;

        private int lastLeftFrontPos, lastLeftRearPos, lastRightRearPos, lastRightFrontPos;

        public DriveLocalizer() {
            leftFront = new OverflowEncoder(new RawEncoder(MecanumDrive.this.leftFront));
            leftRear = new OverflowEncoder(new RawEncoder(MecanumDrive.this.leftBack));
            rightRear = new OverflowEncoder(new RawEncoder(MecanumDrive.this.rightBack));
            rightFront = new OverflowEncoder(new RawEncoder(MecanumDrive.this.rightFront));

            lastLeftFrontPos = leftFront.getPositionAndVelocity().position;
            lastLeftRearPos = leftRear.getPositionAndVelocity().position;
            lastRightRearPos = rightRear.getPositionAndVelocity().position;
            lastRightFrontPos = rightFront.getPositionAndVelocity().position;
        }

        @Override
        public Twist2dIncrDual<Time> updateAndGetIncr() {
            Encoder.PositionVelocityPair leftFrontPosVel = leftFront.getPositionAndVelocity();
            Encoder.PositionVelocityPair leftRearPosVel = leftRear.getPositionAndVelocity();
            Encoder.PositionVelocityPair rightRearPosVel = rightRear.getPositionAndVelocity();
            Encoder.PositionVelocityPair rightFrontPosVel = rightFront.getPositionAndVelocity();

            MecanumKinematics.WheelIncrements<Time> incrs = new MecanumKinematics.WheelIncrements<>(
                    new DualNum<Time>(new double[]{
                            leftFrontPosVel.position - lastLeftFrontPos,
                            leftFrontPosVel.velocity,
                    }).times(inPerTick),
                    new DualNum<Time>(new double[]{
                            leftRearPosVel.position - lastLeftRearPos,
                            leftRearPosVel.velocity,
                    }).times(inPerTick),
                    new DualNum<Time>(new double[]{
                            rightRearPosVel.position - lastRightRearPos,
                            rightRearPosVel.velocity,
                    }).times(inPerTick),
                    new DualNum<Time>(new double[]{
                            rightFrontPosVel.position - lastRightFrontPos,
                            rightFrontPosVel.velocity,
                    }).times(inPerTick)
            );

            lastLeftFrontPos = leftFrontPosVel.position;
            lastLeftRearPos = leftRearPosVel.position;
            lastRightRearPos = rightRearPosVel.position;
            lastRightFrontPos = rightFrontPosVel.position;

            return kinematics.forward(incrs);
        }
    }

    public MecanumDrive(HardwareMap hardwareMap, Pose2d pose) {
        this.pose = pose;

        LynxFirmwareVersion.throwIfAnyModulesBelowVersion(hardwareMap,
                new LynxFirmwareVersion(1, 8, 2));

        for (LynxModule module : hardwareMap.getAll(LynxModule.class)) {
            module.setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
        }

        leftFront = hardwareMap.get(DcMotorEx.class, "leftFront");
        leftBack = hardwareMap.get(DcMotorEx.class, "leftBack");
        rightBack = hardwareMap.get(DcMotorEx.class, "rightBack");
        rightFront = hardwareMap.get(DcMotorEx.class, "rightFront");

        leftFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        leftBack.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        rightBack.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        rightFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        BNO055IMU baseImu = hardwareMap.get(BNO055IMU.class, "imu");
        BNO055IMU.Parameters parameters = new BNO055IMU.Parameters();
        parameters.angleUnit = BNO055IMU.AngleUnit.RADIANS;
        baseImu.initialize(parameters);
        imu = new BNO055Wrapper(baseImu);

        voltageSensor = hardwareMap.voltageSensor.iterator().next();

        localizer = new DriveLocalizer();
    }

    public void setDrivePowers(Twist2d powers) {
        MecanumKinematics.WheelVelocities<Time> wheelVels = kinematics.inverse(Twist2dDual.constant(powers, 1));
        leftFront.setPower(wheelVels.leftFront.get(0));
        leftBack.setPower(wheelVels.leftBack.get(0));
        rightBack.setPower(wheelVels.rightBack.get(0));
        rightFront.setPower(wheelVels.rightFront.get(0));
    }

    public final class FollowTrajectoryAction implements Action {
        public final Trajectory trajectory;
        private TimeTrajectory timeTrajectory;
        private double beginTs;

        private boolean active;
        private final double[] xPoints, yPoints;

        public FollowTrajectoryAction(Trajectory t) {
            this.trajectory = t;
            timeTrajectory = new TimeTrajectory(t);

            List<Double> disps = com.acmerobotics.roadrunner.Math.range(
                    0, t.path.length(),
                    (int) Math.ceil(t.path.length() / 2));
            xPoints = new double[disps.size()];
            yPoints = new double[disps.size()];
            for (int i = 0; i < disps.size(); i++) {
                Pose2d p = t.path.get(disps.get(i), 1).value();
                xPoints[i] = p.trans.x;
                yPoints[i] = p.trans.y;
            }
        }

        @Override
        public void init() {
            beginTs = clock();

            active = true;
        }

        @Override
        public boolean loop(TelemetryPacket p) {
            double t = clock() - beginTs;
            if (t >= timeTrajectory.duration) {
                leftFront.setPower(0);
                leftBack.setPower(0);
                rightBack.setPower(0);
                rightFront.setPower(0);

                active = false;

                return false;
            }

            Pose2dDual<Time> txWorldTarget = timeTrajectory.get(t);

            Twist2d robotVelRobot = updatePoseEstimateAndGetActualVel();

            Twist2dDual<Time> command = new HolonomicController(
                    AXIAL_GAIN, LATERAL_GAIN, HEADING_GAIN,
                    AXIAL_VEL_GAIN, LATERAL_VEL_GAIN, HEADING_VEL_GAIN
            )
                    .compute(txWorldTarget, pose, robotVelRobot);

            MecanumKinematics.WheelVelocities<Time> wheelVels = kinematics.inverse(command);
            double voltage = voltageSensor.getVoltage();
            leftFront.setPower(feedforward.compute(wheelVels.leftFront) / voltage);
            leftBack.setPower(feedforward.compute(wheelVels.leftBack) / voltage);
            rightBack.setPower(feedforward.compute(wheelVels.rightBack) / voltage);
            rightFront.setPower(feedforward.compute(wheelVels.rightFront) / voltage);

            p.put("x", pose.trans.x);
            p.put("y", pose.trans.y);
            p.put("heading (deg)", Math.toDegrees(pose.rot.log()));

            Pose2d error = txWorldTarget.value().minusExp(pose);
            p.put("xError", error.trans.x);
            p.put("yError", error.trans.y);
            p.put("headingError (deg)", Math.toDegrees(error.rot.log()));

            // only draw when active; only one drive action should be active at a time
            Canvas c = p.fieldOverlay();
            drawPoseHistory(c);

            c.setStroke("#4CAF50");
            drawRobot(c, txWorldTarget.value());

            c.setStroke("#3F51B5");
            drawRobot(c, pose);

            return true;
        }

        public void cancel() {
            double t = clock() - beginTs;
            double s = timeTrajectory.profile.get(t).value();
            beginTs += t;
            timeTrajectory = new TimeTrajectory(trajectory.cancel(s));
        }

        @Override
        public void draw(Canvas c) {
            c.setStrokeWidth(1);
            c.setStroke(active ? "#4CAF50FF" : "#4CAF507A");
            c.strokePolyline(xPoints, yPoints);
        }
    }

    public final class TurnAction implements Action {
        private final Pose2d beginPose;

        private final TimeProfile profile;
        private double beginTs;

        private boolean active;

        public TurnAction(Pose2d beginPose, double angle) {
            this.beginPose = beginPose;
            profile = new TimeProfile(Profiles.constantProfile(angle, 0, MAX_ANG_VEL, -MAX_ANG_ACCEL, MAX_ANG_ACCEL).baseProfile);
        }

        @Override
        public void init() {
            beginTs = clock();

            active = true;
        }

        @Override
        public boolean loop(TelemetryPacket p) {
            double t = clock() - beginTs;
            if (t >= profile.duration) {
                leftFront.setPower(0);
                leftBack.setPower(0);
                rightBack.setPower(0);
                rightFront.setPower(0);

                active = false;

                return false;
            }

            DualNum<Time> x = profile.get(t);
            Pose2dDual<Time> txWorldTarget = Rotation2dDual.exp(x).times(beginPose);

            Twist2d robotVelRobot = updatePoseEstimateAndGetActualVel();

            Twist2dDual<Time> command = new HolonomicController(
                    AXIAL_GAIN, LATERAL_GAIN, HEADING_GAIN,
                    AXIAL_VEL_GAIN, LATERAL_VEL_GAIN, HEADING_VEL_GAIN
            )
                    .compute(txWorldTarget, pose, robotVelRobot);

            MecanumKinematics.WheelVelocities<Time> wheelVels = kinematics.inverse(command);
            double voltage = voltageSensor.getVoltage();
            leftFront.setPower(feedforward.compute(wheelVels.leftFront) / voltage);
            leftBack.setPower(feedforward.compute(wheelVels.leftBack) / voltage);
            rightBack.setPower(feedforward.compute(wheelVels.rightBack) / voltage);
            rightFront.setPower(feedforward.compute(wheelVels.rightFront) / voltage);

            Canvas c = p.fieldOverlay();
            drawPoseHistory(c);

            c.setStroke("#4CAF50");
            drawRobot(c, txWorldTarget.value());

            c.setStroke("#3F51B5");
            drawRobot(c, pose);

            return true;
        }

        @Override
        public void draw(Canvas c) {
            c.setFill(active ? "#7C4DFFFF" : "#7C4DFF7A");
            c.fillCircle(beginPose.trans.x, beginPose.trans.y, 2);
        }
    }

    private Twist2d updatePoseEstimateAndGetActualVel() {
        Twist2dIncrDual<Time> incr = localizer.updateAndGetIncr();
        pose = pose.plus(incr.value());

        poseHistory.add(pose);
        while (poseHistory.size() > 100) {
            poseHistory.removeFirst();
        }

        return incr.velocity().value();
    }

    private void drawPoseHistory(Canvas c) {
        double[] xPoints = new double[poseHistory.size()];
        double[] yPoints = new double[poseHistory.size()];

        int i = 0;
        for (Pose2d t : poseHistory) {
            xPoints[i] = t.trans.x;
            yPoints[i] = t.trans.y;

            i++;
        }

        c.setStrokeWidth(1);
        c.setStroke("#3F51B5");
        c.strokePolyline(xPoints, yPoints);
    }

    private static void drawRobot(Canvas c, Pose2d t) {
        final double ROBOT_RADIUS = 9;

        c.setStrokeWidth(1);
        c.strokeCircle(t.trans.x, t.trans.y, ROBOT_RADIUS);

        Vector2d halfv = t.rot.vec().times(0.5 * ROBOT_RADIUS);
        Vector2d p1 = t.trans.plus(halfv);
        Vector2d p2 = p1.plus(halfv);
        c.strokeLine(p1.x, p1.y, p2.x, p2.y);
    }

    public TurnAction turn(Pose2d beginPose, double angle) {
        return new TurnAction(beginPose, angle);
    }
    public TurnAction turnTo(Pose2d beginPose, Rotation2d rot) {
        return new TurnAction(beginPose, rot.minus(beginPose.rot));
    }
    public TurnAction turnTo(Pose2d beginPose, double rot) {
        return turnTo(beginPose, Rotation2d.exp(rot));
    }

    public SafeTrajectoryBuilder trajectoryBuilder(Pose2d beginPose, Rotation2d beginTangent, TrajectoryBuilder.PoseMap poseMap) {
        return new SafeTrajectoryBuilder(
                beginPose, beginTangent, 1e-6,
                0.0, defaultVelConstraint, defaultAccelConstraint, 0.25, poseMap);
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Pose2d beginPose, double beginTangent, TrajectoryBuilder.PoseMap poseMap) {
        return trajectoryBuilder(beginPose, Rotation2d.exp(beginTangent), poseMap);
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Pose2d beginPose, boolean reversed, TrajectoryBuilder.PoseMap poseMap) {
        return trajectoryBuilder(beginPose, beginPose.rot.plus(reversed ? Math.PI : 0), poseMap);
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Pose2d beginPose, TrajectoryBuilder.PoseMap poseMap) {
        return trajectoryBuilder(beginPose, false, poseMap);
    }

    public SafeTrajectoryBuilder trajectoryBuilder(Pose2d beginPose, Rotation2d beginTangent) {
        return new SafeTrajectoryBuilder(
                beginPose, beginTangent, 1e-6,
                0.0, defaultVelConstraint, defaultAccelConstraint, 0.25);
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Pose2d beginPose, double beginTangent) {
        return trajectoryBuilder(beginPose, Rotation2d.exp(beginTangent));
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Pose2d beginPose, boolean reversed) {
        return trajectoryBuilder(beginPose, beginPose.rot.plus(reversed ? Math.PI : 0));
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Pose2d beginPose) {
        return trajectoryBuilder(beginPose, false);
    }

    public SafeTrajectoryBuilder trajectoryBuilder(Trajectory traj, Rotation2d beginTangent, TrajectoryBuilder.PoseMap poseMap) {
        return trajectoryBuilder(traj.path.end(1).value(), beginTangent, poseMap);
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Trajectory traj, double beginTangent, TrajectoryBuilder.PoseMap poseMap) {
        return trajectoryBuilder(traj.path.end(1).value(), beginTangent, poseMap);
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Trajectory traj, boolean reversed, TrajectoryBuilder.PoseMap poseMap) {
        return trajectoryBuilder(traj.path.end(1).value(), reversed, poseMap);
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Trajectory traj, TrajectoryBuilder.PoseMap poseMap) {
        return trajectoryBuilder(traj.path.end(1).value(), poseMap);
    }

    public SafeTrajectoryBuilder trajectoryBuilder(Trajectory traj, Rotation2d beginTangent) {
        return trajectoryBuilder(traj.path.end(1).value(), beginTangent);
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Trajectory traj, double beginTangent) {
        return trajectoryBuilder(traj.path.end(1).value(), beginTangent);
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Trajectory traj, boolean reversed) {
        return trajectoryBuilder(traj.path.end(1).value(), reversed);
    }
    public SafeTrajectoryBuilder trajectoryBuilder(Trajectory traj) {
        return trajectoryBuilder(traj.path.end(1).value());
    }

    public FollowTrajectoryAction followTrajectory(Trajectory trajectory) {
        return new FollowTrajectoryAction(trajectory);
    }
}