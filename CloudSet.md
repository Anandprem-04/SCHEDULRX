# SchedulerX — Cloud Deployment Guide

> Step-by-step guide to deploy a Spring Boot application on AWS EC2 with Nginx reverse proxy and SSL. Written for beginners — no prior cloud experience needed.

---

## Table of Contents

- [What You Need](#what-you-need)
- [Step 1 — Build the JAR](#step-1--build-the-jar)
- [Step 2 — Launch EC2 Instance](#step-2--launch-ec2-instance)
- [Step 3 — Connect to EC2](#step-3--connect-to-ec2)
- [Step 4 — Install Java](#step-4--install-java)
- [Step 5 — Install Nginx](#step-5--install-nginx)
- [Step 6 — Configure DNS](#step-6--configure-dns)
- [Step 7 — Upload the JAR](#step-7--upload-the-jar)
- [Step 8 — Configure Nginx](#step-8--configure-nginx)
- [Step 9 — Install SSL Certificate](#step-9--install-ssl-certificate)
- [Step 10 — Run as a Service](#step-10--run-as-a-service)
- [Useful Commands](#useful-commands)

---

## What You Need

Before starting make sure you have:

- An **AWS account** — sign up at [aws.amazon.com](https://aws.amazon.com) (free tier available)
- A **domain name** — from any registrar (Namecheap, GoDaddy, etc.)
- **Java 17** installed on your local machine
- Your project built and tested locally

---

## Step 1 — Build the JAR

On your local machine, build the production JAR:

```bash
# Linux / macOS
./mvnw clean package -DskipTests

# Windows (PowerShell)
.\mvnw clean package -DskipTests
```

The JAR file will be generated at:
```
target/schedulrx-0.0.1-SNAPSHOT.jar
```

> `-DskipTests` skips running tests to speed up the build. Remove it if you want tests to run.

---

## Step 2 — Launch EC2 Instance

1. Log in to **AWS Console** → search for **EC2** → click **Launch Instance**

2. Fill in the settings:

| Setting | Recommended Value |
|---|---|
| Name | anything you like (e.g. `my-app-server`) |
| AMI (Operating System) | **Ubuntu Server 24.04 LTS** — Free tier eligible |
| Instance Type | **t3.micro** — Free tier eligible |
| Key Pair | Click **Create new key pair** → give it a name → RSA → `.pem` format → click Create |

> ⚠️ The `.pem` key file downloads automatically. **Save it somewhere safe — you cannot download it again.** This file is how you connect to your server.

3. Under **Network Settings → Edit**, configure the firewall rules:

| Type | Port | Source | Purpose |
|---|---|---|---|
| SSH | 22 | My IP | Lets only you connect via terminal |
| HTTP | 80 | Anywhere | Allows web traffic |
| HTTPS | 443 | Anywhere | Allows secure web traffic |

4. Leave storage as default (8 GB is fine)

5. Click **Launch Instance**

6. Go to **EC2 → Instances** → wait for your instance to show **Running** → copy the **Public IPv4 address**

---

## Step 3 — Connect to EC2

Open a terminal (PowerShell on Windows, Terminal on macOS/Linux) and navigate to where your `.pem` file is saved:

```powershell
# Windows
ssh -i "path\to\your-key.pem" ubuntu@YOUR_EC2_IP
```

```bash
# macOS / Linux — fix permissions first
chmod 400 path/to/your-key.pem
ssh -i path/to/your-key.pem ubuntu@YOUR_EC2_IP
```

Replace `YOUR_EC2_IP` with the public IP you copied.

> If it asks "Are you sure you want to continue connecting?" type `yes` and press Enter.

You should see the Ubuntu welcome screen — you are now inside your server.

---

## Step 4 — Install Java

Run these commands on your EC2 server:

```bash
# Update package list
sudo apt update

# Install Java 17
sudo apt install openjdk-17-jdk -y

# Verify installation
java -version
```

Expected output:
```
openjdk version "17.x.x" ...
```

---

## Step 5 — Install Nginx

Nginx will act as a reverse proxy — it receives requests on port 80/443 and forwards them to your Spring Boot app on port 8080.

```bash
# Install Nginx
sudo apt install nginx -y

# Start Nginx
sudo systemctl start nginx

# Enable auto-start on server reboot
sudo systemctl enable nginx

# Verify it's running
sudo systemctl status nginx
```

You should see `active (running)` in the output.

Open your browser and visit `http://YOUR_EC2_IP` — you should see the **Nginx welcome page**. This confirms Nginx is working.

---

## Step 6 — Configure DNS

Log in to your domain registrar and add a new **A Record** pointing your subdomain to your EC2 IP:

| Type | Host | Value | TTL |
|---|---|---|---|
| A Record | `your-subdomain` | `YOUR_EC2_IP` | Automatic |

**Example:** If your domain is `example.com` and you want `app.example.com`, set Host to `app`.

After saving, wait 5–30 minutes for DNS to propagate. Verify it's working:

```powershell
nslookup your-subdomain.yourdomain.com
```

It should return your EC2 IP address.

---

## Step 7 — Upload the JAR

From your **local machine** (not EC2), upload the JAR to the server:

```powershell
# Windows
scp -i "path\to\your-key.pem" "path\to\target\schedulrx-0.0.1-SNAPSHOT.jar" ubuntu@YOUR_EC2_IP:/home/ubuntu/
```

```bash
# macOS / Linux
scp -i path/to/your-key.pem target/schedulrx-0.0.1-SNAPSHOT.jar ubuntu@YOUR_EC2_IP:/home/ubuntu/
```

> `scp` means "secure copy" — it transfers files over SSH.

Once uploaded, test it runs on EC2:

```bash
java -jar /home/ubuntu/schedulrx-0.0.1-SNAPSHOT.jar
```

Wait for this line:
```
Started SchedulrxApplication in X.XXX seconds
```

Press `Ctrl+C` to stop it for now — we will set it up properly in Step 10.

---

## Step 8 — Configure Nginx

Create a new Nginx configuration file for your app:

```bash
sudo nano /etc/nginx/sites-available/myapp
```

Paste the following — replace `your-subdomain.yourdomain.com` with your actual domain:

```nginx
server {
    listen 80;
    server_name your-subdomain.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name your-subdomain.yourdomain.com;

    ssl_certificate     /etc/nginx/ssl/fullchain.crt;
    ssl_certificate_key /etc/nginx/ssl/private.key;

    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    location / {
        proxy_pass         http://localhost:8080;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }
}
```

Save with `Ctrl+X` → `Y` → `Enter`

Enable the site and reload Nginx:

```bash
# Enable the site
sudo ln -s /etc/nginx/sites-available/myapp /etc/nginx/sites-enabled/

# Test the config for errors
sudo nginx -t

# Reload Nginx
sudo systemctl reload nginx
```

`nginx -t` should say `syntax is ok` and `test is successful`.

---

## Step 9 — Install SSL Certificate

SSL gives your site `https://` and the padlock icon. There are two ways:

---

### Option A — Free SSL via Certbot (Recommended for beginners)

Certbot is a free tool that gets you a trusted SSL certificate from Let's Encrypt in under a minute.

```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx -y

# Get certificate (replace with your domain)
sudo certbot --nginx -d your-subdomain.yourdomain.com
```

Follow the prompts:
1. Enter your email address
2. Agree to terms → type `Y`
3. Share email with EFF → type `N` (optional)

Certbot automatically configures Nginx with SSL. Visit `https://your-subdomain.yourdomain.com` — you should see the padlock 🔒.

> Certbot certificates expire every 90 days but **auto-renew automatically**. No action needed.

---

### Option B — Paid SSL Certificate (e.g. Namecheap PositiveSSL)

**Step 1 — Generate a CSR on EC2:**

```bash
sudo mkdir -p /etc/nginx/ssl

sudo openssl req -new -newkey rsa:2048 -nodes \
  -keyout /etc/nginx/ssl/private.key \
  -out /etc/nginx/ssl/request.csr
```

Fill in the prompts when asked:
- **Common Name** → `your-subdomain.yourdomain.com` ← this is the most important field
- Leave challenge password blank — just press Enter
- Leave optional company name blank — just press Enter

View and copy the CSR:
```bash
sudo cat /etc/nginx/ssl/request.csr
```

Copy everything including `-----BEGIN CERTIFICATE REQUEST-----` and `-----END CERTIFICATE REQUEST-----`.

**Step 2 — Submit CSR to your SSL provider:**

Paste the CSR into your SSL provider's activation/reissue form. Choose **DNS validation** (CNAME method). They will give you a CNAME record to add to your DNS.

**Step 3 — Add CNAME record for validation:**

Go to your domain registrar → DNS settings → add the CNAME record exactly as provided. Wait for validation (usually 15–60 minutes).

**Step 4 — Download and install the certificate:**

Once issued, you will receive a zip file with `.crt` and `.ca-bundle` files. Upload them to EC2:

```bash
# From local machine
scp -i "your-key.pem" certificate.crt ca-bundle.file ubuntu@YOUR_EC2_IP:/home/ubuntu/
```

On EC2, move and combine the files:

```bash
sudo mv /home/ubuntu/certificate.crt /etc/nginx/ssl/
sudo mv /home/ubuntu/ca-bundle.file /etc/nginx/ssl/

# Combine cert + chain into one file (required by Nginx)
sudo bash -c 'cat /etc/nginx/ssl/certificate.crt > /etc/nginx/ssl/fullchain.crt'
sudo bash -c 'echo "" >> /etc/nginx/ssl/fullchain.crt'
sudo bash -c 'cat /etc/nginx/ssl/ca-bundle.file >> /etc/nginx/ssl/fullchain.crt'
```

Test and reload Nginx:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

---

## Step 10 — Run as a Service

Running the JAR directly in the terminal stops when you close the terminal. A `systemd` service keeps it running permanently.

```bash
sudo nano /etc/systemd/system/myapp.service
```

Paste this:

```ini
[Unit]
Description=My Spring Boot Application
After=network.target

[Service]
User=ubuntu
ExecStart=/usr/bin/java -jar /home/ubuntu/schedulrx-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Save with `Ctrl+X` → `Y` → `Enter`

```bash
# Load the new service
sudo systemctl daemon-reload

# Enable auto-start on reboot
sudo systemctl enable myapp

# Start the service now
sudo systemctl start myapp

# Verify it's running
sudo systemctl status myapp
```

You should see `active (running)`. Visit your domain — the app is live! 🚀

---

## Useful Commands

### Manage the App Service

```bash
# Start / Stop / Restart
sudo systemctl start myapp
sudo systemctl stop myapp
sudo systemctl restart myapp

# Check if running
sudo systemctl status myapp

# View live logs
sudo journalctl -u myapp -f
```

### Manage Nginx

```bash
# Test config for errors
sudo nginx -t

# Reload config without downtime
sudo systemctl reload nginx

# Restart Nginx
sudo systemctl restart nginx

# View error logs
sudo tail -f /var/log/nginx/error.log
```

### Update the App (New JAR Version)

```bash
# 1. Upload new JAR from local machine
scp -i "your-key.pem" target/schedulrx-0.0.1-SNAPSHOT.jar ubuntu@YOUR_EC2_IP:/home/ubuntu/

# 2. Restart the service on EC2
sudo systemctl restart myapp
```

### Check What's Running on Port 8080

```bash
sudo lsof -i :8080
```

---

> **Tip:** If something isn't working, always check the logs first.
> App logs: `sudo journalctl -u myapp -f`
> Nginx logs: `sudo tail -f /var/log/nginx/error.log`
