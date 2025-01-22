import requests

def send_notification():
    webhook_url = "<WEBHOOK_URL>"
    message = {
        "text": "Bitbucket pipeline executed successfully!"
    }
    response = requests.post(webhook_url, json=message)
    if response.status_code == 200:
        print("Notification sent successfully!")
    else:
        print(f"Failed to send notification: {response.status_code}")

if __name__ == "__main__":
    send_notification()
