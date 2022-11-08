package org.firstinspires.ftc.teamcode;

import static java.lang.Math.abs;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

@TeleOp
public class TestLift extends OpMode {
    private DcMotor lift;
    private Servo clawLeft;
    private Servo clawRight;
    private boolean clawOpen = true;
    private DcMotor left;
    private DcMotor right;
    private int liftPosition = 0;
    private final int liftZero = 0;
    private Junction targetJunction = null;
    private ElapsedTime runtime = new ElapsedTime();


    @Override
    public void init() {
        lift = hardwareMap.dcMotor.get("lift");
        lift.setDirection(DcMotorSimple.Direction.REVERSE);
        lift.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        lift.setTargetPosition(lift.getCurrentPosition());
        lift.setMode(DcMotor.RunMode.RUN_TO_POSITION);

        clawLeft = hardwareMap.servo.get("clawLeft");
        clawRight = hardwareMap.servo.get("clawRight");

        left = hardwareMap.dcMotor.get("left");
        right = hardwareMap.dcMotor.get("right");
        right.setDirection(DcMotorSimple.Direction.REVERSE);
        left.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        right.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
    }

    @Override
    public void loop() {
        double drive = -gamepad1.left_stick_y;
        double turn = -gamepad1.right_stick_x;
        double leftPower    = Range.clip(drive + turn, -1.0, 1.0) ;
        double rightPower   = Range.clip(drive - turn, -1.0, 1.0) ;
        left.setPower(leftPower);
        right.setPower(rightPower);

        //todo move to another class
        if (gamepad1.dpad_left) {
            clawOpen = false;
            if (gamepad1.y) {
                targetJunction = Junction.HIGH;
            } else if (gamepad1.x) {
                targetJunction = Junction.MEDIUM;
            } else if (gamepad1.b) {
                targetJunction = Junction.LOW;
            } else if (gamepad1.a) {
                targetJunction = Junction.GROUND;
            }
            runtime.reset();
        } else if (gamepad1.dpad_right) {
            clawOpen = true;
        }
        clawLeft.setPosition(clawOpen ? 0 : 1);
        clawRight.setPosition(clawOpen ? 1 : 0);

        if (runtime.milliseconds() > 500 && targetJunction != null) {
            liftPosition = liftZero + targetJunction.getHeightOffset();
            targetJunction = null;
        }

        if (gamepad1.dpad_up) {
            liftPosition += 10;
        } else if (gamepad1.dpad_down) {
            liftPosition -= 10;
        } else if (gamepad1.right_bumper) {
            liftPosition = liftZero;
        }
        liftPosition = Range.clip(liftPosition, liftZero, liftZero + 4100);

        lift.setTargetPosition(liftPosition);
        lift.setPower(abs(liftPosition - lift.getCurrentPosition()) / 100.0);

        telemetry.addData("liftCurrentPosition=", lift.getCurrentPosition());
        telemetry.addData("liftTargetPosition=", lift.getTargetPosition());
        telemetry.update();
    }

    enum Junction {
        GROUND {
            @Override
            public int getHeightOffset() {
                return (int) (2 * 120);
            }
        },
        LOW {
            @Override
            public int getHeightOffset() {
                return (int) (13.5 * 120);
            }
        },
        MEDIUM {
            @Override
            public int getHeightOffset() {
                return (int) (23.5 * 120);
            }
        },
        HIGH {
            @Override
            public int getHeightOffset() {
                return (int) (33.5 * 120);
            }
        };
        public int getHeightOffset() {
            return 0;
        }
    }
}
