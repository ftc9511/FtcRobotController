package org.firstinspires.ftc.teamcode;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotorController;

import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;
import org.firstinspires.ftc.robotcore.external.navigation.VoltageUnit;

@Autonomous
public class TestBattery extends LinearOpMode {
    @Override
    public void runOpMode() {
        LynxModule lynxModule = hardwareMap.getAll(LynxModule.class).get(0);

        DcMotorController motorController = hardwareMap.dcMotorController.iterator().next();

        waitForStart();

        double v1 = lynxModule.getInputVoltage(VoltageUnit.VOLTS);
        double i1 = lynxModule.getCurrent(CurrentUnit.AMPS);
        telemetry.addData("Idle", "Voltage = %.2f, Current = %.2f", v1, i1);

        for (int motor = 0; motor < 4; motor++) {
            motorController.setMotorPower(motor, 0.5);
        }
        sleep(500);

        double v2 = lynxModule.getInputVoltage(VoltageUnit.VOLTS);
        double i2 = lynxModule.getCurrent(CurrentUnit.AMPS);
        double internalR2 = (v1 - v2)/i2;
        telemetry.addData("Low load", "Voltage = %.2f, Current = %.2f, IR = %.3f", v2, i2, internalR2);

        for (int motor = 0; motor < 4; motor++) {
            motorController.setMotorPower(motor, 1);
        }
        sleep(500);

        double v3 = lynxModule.getInputVoltage(VoltageUnit.VOLTS);
        double i3 = lynxModule.getCurrent(CurrentUnit.AMPS);
        double internalR3 = (v1 - v3)/i3;
        telemetry.addData("High load", "Voltage = %.2f, Current = %.2f, IR = %.3f", v3, i3, internalR3);
        telemetry.update();

        for (int motor = 0; motor < 4; motor++) {
            motorController.setMotorPower(motor, 0);
        }
        sleep(30000);
    }
}