/**
 *  GMC Map Driver (gmcmap.com)
 *  IMPORT URL: https://raw.githubusercontent.com/WBEVAN/gmcmapdata/master/gmcmap_device.groovy
 *
 *  This driver implements the ability to poll for a single registered Gieger counter from the GMC Map Website
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  WHATS NEW in 1.0
 *
 *  - First Driver, Supports URL change, Gieger counter and refresh frequence (Default 10 Mins)
 *
 *  Changelog:
 *
 *  1.0 (01 Aug 2021) - Initial release
 *
*/



import groovy.json.JsonSlurper

metadata {

 definition (name: "GMC Map Driver", namespace: "bevan", author: "Wayne Bevan", importUrl: "https://raw.githubusercontent.com/XXXXXXX") {

 capability "Sensor"

 command "refreshData"
 command "setPollInterval", [[
			name: "Poll Interval in minutes",
			constraints: [ "default", "1 minute", "5 minutes",  "10 minutes",
						  "30 minutes"],
			type: "ENUM"]]

 attribute 'UTCTimeStamp', 'date'
 attribute 'LocalTimeSTamp', 'date'
 attribute 'LastRefreshedTime', 'date'
 attribute 'CPM', 'number'
 attribute 'ACPM', 'number'
 attribute 'uSv', 'number'
 attribute 'LastError', 'string'


 }

 preferences {

       input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false

       input "url",  "string", title: "URL for GMC Map",  defaultValue: "https://www.gmcmap.com/historyData-plain.asp",  displayDuringSetup: true, required: true
       input "GeigerCounterID",  "string", title: "Geiger Counter ID",  defaultValue: "7674635781",  displayDuringSetup: true, required: true



 }

}

///
/// lifecycle events
///

void installed() {
    log.info 'Driver installed() called'
    state.pollInterval = "10 minutes"
}

void updated() {
    log.info 'Driver updated() called'

    state.clear()

    //Unschedule any existing schedules
    unschedule()

    // perform health check every 5 minutes
//    runEvery5Minutes('healthCheck')

    // disable logs in 30 minutes
    if (settings.logEnable) runIn(1800, logsOff)

    initialize()
}


void initialize() {
    log.info 'Driver initialize() called'

    unschedule(initialize)

}

def refreshData(){

    logDebug "Refreshing Data from ${url} Param_ID=${GeigerCounterID}"
    // Note we pull the data back in UTC then covert to local time
    def paramsSettings = [uri: "${url}?Param_ID=${GeigerCounterID}"]

try {
    httpGet(paramsSettings) {
        respSettings -> respSettings.headers.each {
        logDebug "ResponseSettings: ${it.name} : ${it.value}"
    }


        slurper = new JsonSlurper()
        readings = slurper.parseText(respSettings.data.text())

        logDebug "params: ${paramsSettings}"
        logDebug "response contentType: ${respSettings.contentType}"
	    logDebug "response data: ${respSettings.data}"

        logDebug "UTC Time ${readings.time}"
        logDebug "Local Time ${timeUtcToLocal(readings.time)}"
        logDebug "CPM ${readings.CPM}"
        logDebug "ACPM ${readings.ACPM}"
        logDebug "uSv ${readings.uSv}"

        sendEvent(name: 'UTCTimeStamp', value: readings.time )
        sendEvent(name: 'LocalTimeSTamp', value: timeUtcToLocal(readings.time))
        sendEvent(name: 'CPM', value: readings.CPM, unit: 'CPM')
        sendEvent(name: 'ACPM', value: readings.ACPM, unit: 'ACPM')
        sendEvent(name: 'uSv', value: readings.uSv, unit: 'uSv')

        def date = new Date()
        def sdf = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
        sendEvent(name: 'LastRefreshedTime', value: sdf.format(date))



  } // End try
       } catch (e) {

            def date = new Date()
            def sdf = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
            errTxt = "At ${sdf.format(date)} something went wrong: $e"
            sendEvent(name: 'LastError', value: errTxt)
            log.error errTxt
       }

} // End Refresh Status


// Polling Functions
//	===== Preference Methods =====
def setPolling() {
	def message = "Setting Poll Intervals."
	def interval = "10 minutes"
	if (state.pollInterval) {
		interval = state.pollInterval
	}
	message += "\n\t\t\t Refresh Data Polling set to ${interval}."
	setPollInterval(interval)
	state.remove("powerPollInterval")
	state.remove("powerPollWarning")
	return message
}

def setPollInterval(interval) {
	logDebug("setPollInterval: interval = ${interval}.")
	if (interval == "default" || interval == "off") {
		interval = "10 minutes"
	} else  {
		interval = "1 minute"
	}

    state.pollInterval = interval
	schedInterval("refreshData", interval)
}

def schedInterval(pollType, interval) {
	logDebug("schedInterval: type = ${pollType}, interval = ${interval}.")
	def message = ""
	def pollInterval = interval.substring(0,2).toInteger()
	if (interval.contains("sec")) {
		def start = Math.round((pollInterval-1) * Math.random()).toInteger()
		schedule("${start}/${pollInterval} * * * * ?", pollType)
		message += "${pollType} Interval set to ${interval} seconds."
	} else {
		def start = Math.round(59 * Math.random()).toInteger()
		schedule("${start} */${pollInterval} * * * ?", pollType)
		message += "${pollType} Interval set to ${interval} minutes."
	}
}

// Logging Functions
void logsOff() {
    log.info 'Logging disabled.'
    device.updateSetting('logEnable',[value:'false',type:'bool'])
}



private logDebug(msg) {
    if (settings.logEnable) {
        log.debug "$msg"
    }
}

private String timeUtcToLocal(String time) {
  //
  // Convert a UTC date and time in the format "yyyy-MM-dd+HH:mm:ss" to a local time with locale format
  //
  try {
    // Create a UTC formatter and parse the given time
    java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    format.setTimeZone(TimeZone.getTimeZone("UTC"));

    Date date = format.parse(time);


    // Create a local/locale formatter and format the given time
    format = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    time = format.format(date);
  }
  catch (Exception e) {
    log.error "Exception in timeUtcToLocal(): ${e}"
  }

  return (time);
}
