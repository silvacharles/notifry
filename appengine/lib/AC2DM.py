
import datetime
import logging

class A2CDM:
	def __init__(self, token):
		self.token = token

	def send(self, message, device):
		# Prepare for our request.
		params = {}
		params['collapse_key'] = 'testing'
		params['registration_id'] = device.deviceKey
		params['delay_until_idle'] = 0

		params['data.title'] = message.title
		params['data.message'] = message.message
		if message.url:
			params['data.url'] = message.url
		params['data.timestamp'] = str(message.timestamp)

		result = fetch(
			"https://android.apis.google.com/c2dm/send",
			urllib.urlencode(params), # POST body
			"POST", # HTTP method
			{
				'Authorization': 'GoogleLogin auth=' + self.token.token
			}, # Additional headers
			False, # Don't allow a truncated response
			True, # Do follow redirects.
			None, # Default timeout/deadline
			True # Validate the SSL certificate/issuer/time (important!)
		)

		if result.status_code == 200:
			# Success!
			# The result body is a queue id. Store it.
			message.googleQueueId = result.content.trim()
			message.deliveredToGoogle = True
			message.lastDeliveryAttempt = datetime.datetime.now()
			message.put()
			return True
		else:
			# Failed to send. Log the error message.
			message.lastDeliveryAttempt = datetime.datetime.now()
			message.deliveredToGoogle = False
			message.put()

			logging.error('Unable to send message ' + message.key().id() + ' to Google: Status code ' + str(result.status_code) + ' body ' + result.content)
			return False