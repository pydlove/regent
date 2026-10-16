#!/bin/bash
systemctl start regent-relay
systemctl status regent-relay --no-pager | head -5
