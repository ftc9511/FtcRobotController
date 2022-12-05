package org.firstinspires.ftc.teamcode;

import static java.lang.Math.abs;
import static java.lang.Math.max;

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
    private DcMotor frontLeft;
    private DcMotor frontRight;
    private DcMotor backLeft;
    private DcMotor backRight;
    private int liftPosition = 0;
    private int liftZero = 0;
    private Junction targetJunction = null;
    private final ElapsedTime runtime = new ElapsedTime();


    @Override
    public void init() {
        lift = hardwareMap.dcMotor.get("lift");
        lift.setDirection(DcMotorSimple.Direction.REVERSE);
        lift.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        liftZero = lift.getCurrentPosition();
        lift.setTargetPosition(lift.getCurrentPosition());
        lift.setMode(DcMotor.RunMode.RUN_TO_POSITION);

        clawLeft = hardwareMap.servo.get("clawLeft");
        clawRight = hardwareMap.servo.get("clawRight");

        frontLeft = hardwareMap.dcMotor.get("fl");
        frontRight = hardwareMap.dcMotor.get("fr");
        backLeft = hardwareMap.dcMotor.get("bl");
        backRight = hardwareMap.dcMotor.get("br");
        frontLeft.setDirection(DcMotorSimple.Direction.REVERSE);
        backLeft.setDirection(DcMotorSimple.Direction.REVERSE);

        frontLeft.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        frontRight.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        backLeft.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        backRight.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
    }

    @Override
    public void loop() {
        double y = -gamepad1.left_stick_y; // Remember, this is reversed!
        double x = gamepad1.left_stick_x * 1.1; // Counteract imperfect strafing
        double rx = gamepad1.right_stick_x;

        double denominator = Math.max(Math.abs(y) + Math.abs(x) + Math.abs(rx), 1);

        frontLeft.setPower((y + x + rx) / denominator);
        backLeft.setPower((y - x + rx) / denominator);
        frontRight.setPower((y - x - rx) / denominator);
        backRight.setPower((y + x - rx) / denominator);

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
        lift.setPower(max(0.2, abs(liftPosition - lift.getCurrentPosition()) / 500.0));

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
