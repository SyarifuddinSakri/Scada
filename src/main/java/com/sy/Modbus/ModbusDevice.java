package com.sy.Modbus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sy.Modbus.Entity.RecordLog;
import com.sy.Modbus.Repo.AlarmLogRepo;
import com.sy.Modbus.Repo.RecordLogRepo;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@EnableScheduling
@Component
@Scope("prototype")
public class ModbusDevice extends ModbusDeviceTransaction {
	@Autowired
	Server server;
	@Autowired
	RecordLog recordLog;
	@Autowired
	RecordLogRepo recordLogRepo;
	ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	@Autowired
	public ModbusDevice(String deviceName, JSONObject deviceData, AlarmLogRepo alarmLogRepo, RecordLogRepo recordLogRepo)
			throws JSONException {
		super(deviceName, deviceData, alarmLogRepo);
		this.recordLogRepo = recordLogRepo;
		samplingAnalog(scheduler);

	}

	@Override
	public void onValueChangeDi(String key, boolean currentValue) {
		if (currentValue) {
			if (digitalAlarmTextIfTrue.containsKey(key)) {
				writeLog(getTime() + digitalAlarmTextIfTrue.get(key));
			}
		} else {
			if (digitalAlarmTextIfFalse.containsKey(key)) {
				writeLog(getTime() + digitalAlarmTextIfFalse.get(key));
			}
		}
	}

	@Override
	public void onValueChangeAi(String key, Integer currentValue) {
		try {
			Integer lowerLimit = analogTrigThresholdMin.get(key);
			Integer upperLimit = analogTrigThresholdMax.get(key);

			if (lowerLimit != null && currentValue < lowerLimit && analogAllowWrite.get(key)) {
				analogAllowWrite.put(key, false);
				writeLog(getTime() + analogAlarmTextIfMin.get(key));
			} else if (upperLimit != null && currentValue > upperLimit && analogAllowWrite.get(key)) {
				analogAllowWrite.put(key, false);
				writeLog(getTime() + analogAlarmTextIfMax.get(key));
			} else if ((lowerLimit == null || currentValue > lowerLimit) && (upperLimit == null || currentValue < upperLimit)
					&& !analogAllowWrite.get(key)) {
				// reset allowing write for the analog alarm
				analogAllowWrite.put(key, true);
				writeLog(getTime() + analogAlarmTextIfNormal.get(key));
			}
		} catch (Exception e) {
			System.out.println("Exception occurs during checking the Analog alarm in onValueChangeAi method" + e);
		}
	}

	@Override
	public void onValueChangeText(String key, String currentValue) {

	}

	@Override
	public void onTick() {
		// server.dataList.put(deviceName, data);

	}

	@Override
	public void onValueChangeFloat(String key, Float currentValue) {
		try {
			Float lowerLimit = floatTrigThresholdMin.get(key);
			Float upperLimit = floatTrigThresholdMax.get(key);

			if (lowerLimit != null && currentValue < lowerLimit && floatAllowWrite.get(key)) {
				floatAllowWrite.put(key, false);
				writeLog(getTime() + floatAlarmTextIfMin.get(key));
			} else if (upperLimit != null && currentValue > upperLimit && floatAllowWrite.get(key)) {
				floatAllowWrite.put(key, false);
				writeLog(getTime() + floatAlarmTextIfMax.get(key));
			} else if ((lowerLimit == null || currentValue > lowerLimit) && (upperLimit == null || currentValue < upperLimit)
					&& !floatAllowWrite.get(key)) {
				floatAllowWrite.put(key, true);
				writeLog(getTime() + floatAlarmTextIfNormal.get(key));
			}

		} catch (Exception e) {

		}
	}

	protected void samplingAnalog(ScheduledExecutorService scheduler) {
		LocalDateTime now = LocalDateTime.now();
		// LocalDateTime nextHour = now.plusHours(1).truncatedTo(ChronoUnit.HOURS);
		LocalDateTime nextHour = now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
		long delay = now.until(nextHour, ChronoUnit.MILLIS);

		scheduler.schedule(() -> {
			try {
				this.wrAddrrPermit.acquire();
				if (data.has("Analog") && !data.isNull("Analog")) {
					// need to be casted into string as some error occurs if directly taken from
					// JSONObject
					String dataString = data.toString();
					JSONObject dataJson = new JSONObject(dataString);
					JSONObject dataAnalog = dataJson.getJSONObject("Analog");

					// loop through all the processNeedToRecord() return function in Transaction
					for (String tag : tagNeedToRecordAnalog) {
						// Please make database transaction for the sampling Analog
						System.out.println("This is tag : " + tag + " this is the value : " +
								dataAnalog.get(tag));
						System.out.println("Saving record " + tag);
						RecordLog record = new RecordLog();
						record.setData(dataAnalog.get(tag).toString());
						record.setTagName(tag);
						recordLogRepo.save(record);
						System.out.println("Record saved " + tag + " " + dataAnalog.get(tag));
					}
				}
				this.wrAddrrPermit.release();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				this.wrAddrrPermit.release();
				System.out.println("Cannot make sampling data " + e + deviceName);
			} finally {
				this.wrAddrrPermit.release();
			}
		}, delay, TimeUnit.MILLISECONDS);
	}
}
